package coop.rchain.node.runtime

import cats.Parallel
import cats.effect.concurrent.{Deferred, Ref}
import cats.effect.{Concurrent, ContextShift, Sync}
import cats.mtl.ApplicativeAsk
import cats.syntax.all._
import coop.rchain.blockstorage.KeyValueBlockStore
import coop.rchain.blockstorage.casperbuffer.CasperBufferKeyValueStorage
import coop.rchain.blockstorage.dag.BlockDagKeyValueStorage
import coop.rchain.blockstorage.deploy.KeyValueDeployStorage
import coop.rchain.blockstorage.finality.LastFinalizedKeyValueStorage
import coop.rchain.casper._
import coop.rchain.casper.api.BlockReportAPI
import coop.rchain.casper.blocks.BlockProcessor
import coop.rchain.casper.blocks.proposer.{Proposer, ProposerResult}
import coop.rchain.casper.engine.{BlockRetriever, CasperLaunch, EngineCell, Running}
import coop.rchain.casper.protocol.BlockMessage
import coop.rchain.casper.state.instances.{BlockStateManagerImpl, ProposerState}
import coop.rchain.casper.storage.RNodeKeyValueStoreManager
import coop.rchain.casper.storage.RNodeKeyValueStoreManager.legacyRSpacePathPrefix
import coop.rchain.casper.util.comm.{CasperPacketHandler, CommUtil}
import coop.rchain.casper.util.rholang.RuntimeManager
import coop.rchain.comm.rp.Connect.ConnectionsCell
import coop.rchain.comm.rp.RPConf
import coop.rchain.comm.transport.TransportLayer
import coop.rchain.crypto.PrivateKey
import coop.rchain.crypto.codec.Base16
import coop.rchain.metrics.{Metrics, Span}
import coop.rchain.models.BlockHash.BlockHash
import coop.rchain.models.{BindPattern, ListParWithRandom, Par, TaggedContinuation}
import coop.rchain.monix.Monixable
import coop.rchain.node.api.AdminWebApi.AdminWebApiImpl
import coop.rchain.node.api.WebApi.WebApiImpl
import coop.rchain.node.api.{AdminWebApi, WebApi}
import coop.rchain.node.configuration.NodeConf
import coop.rchain.node.diagnostics
import coop.rchain.node.runtime.NodeRuntime._
import coop.rchain.node.state.instances.RNodeStateManagerImpl
import coop.rchain.node.web.ReportingRoutes
import coop.rchain.node.web.ReportingRoutes.ReportingHttpRoutes
import coop.rchain.p2p.effects.PacketHandler
import coop.rchain.rholang.interpreter.RhoRuntime
import coop.rchain.rspace.state.instances.RSpaceStateManagerImpl
import coop.rchain.rspace.syntax._
import coop.rchain.rspace.{Match, RSpace}
import coop.rchain.shared._
import fs2.concurrent.Queue
import monix.execution.Scheduler

import java.nio.file.Files

object Setup {
  def setupNodeProgram[F[_]: Monixable: Concurrent: Parallel: ContextShift: Time: TransportLayer: LocalEnvironment: Log: EventLog: Metrics](
      rpConnections: ConnectionsCell[F],
      rpConfAsk: ApplicativeAsk[F, RPConf],
      commUtil: CommUtil[F],
      blockRetriever: BlockRetriever[F],
      conf: NodeConf,
      eventPublisher: EventPublisher[F]
  )(implicit mainScheduler: Scheduler): F[
    (
        PacketHandler[F],
        APIServers,
        CasperLoop[F],
        CasperLoop[F],
        EngineInit[F],
        CasperLaunch[F],
        ReportingHttpRoutes[F],
        WebApi[F],
        AdminWebApi[F],
        Option[Proposer[F]],
        Queue[F, (Casper[F], Boolean, Deferred[F, ProposerResult])],
        // TODO move towards having a single node state
        Option[Ref[F, ProposerState[F]]],
        BlockProcessor[F],
        Ref[F, Set[BlockHash]],
        Queue[F, (Casper[F], BlockMessage)],
        Option[ProposeFunction[F]]
    )
  ] =
    for {
      // In memory state for last approved block
      lab <- LastApprovedBlock.of[F]

      span = if (conf.metrics.zipkin)
        diagnostics.effects
          .span(conf.protocolServer.networkId, conf.protocolServer.host.getOrElse("-"))
      else Span.noop[F]

      // RNode key-value store manager / manages LMDB databases
      oldRSpacePath          = conf.storage.dataDir.resolve(s"$legacyRSpacePathPrefix/history/data.mdb")
      legacyRSpaceDirSupport <- Sync[F].delay(Files.exists(oldRSpacePath))
      rnodeStoreManager      <- RNodeKeyValueStoreManager(conf.storage.dataDir, legacyRSpaceDirSupport)

      // TODO: Old BlockStore migration message, remove after couple of releases from v0.11.0.
      oldBlockStoreExists = conf.storage.dataDir.resolve("blockstore/storage").toFile.exists
      oldBlockStoreMsg    = s"""
       |Old file-based block storage detected (blockstore/storage). To use this version of RNode please first do the migration.
       |Migration should be done with RNode version v0.10.2. More info can be found in PR:
       |https://github.com/rchain/rchain/pull/3342
      """.stripMargin
      _                   <- new Exception(oldBlockStoreMsg).raiseError.whenA(oldBlockStoreExists)

      // Block storage
      blockStore <- KeyValueBlockStore(rnodeStoreManager)

      // Last finalized Block storage
      lastFinalizedStorage <- {
        for {
          lastFinalizedBlockDb <- rnodeStoreManager.store("last-finalized-block")
          lastFinalizedStore   = LastFinalizedKeyValueStorage(lastFinalizedBlockDb)
        } yield lastFinalizedStore
      }

      // Migrate LastFinalizedStorage to BlockDagStorage
      lfbMigration = Log[F].info("Migrating LastFinalizedStorage to BlockDagStorage.") *>
        lastFinalizedStorage.migrateLfb(rnodeStoreManager, blockStore)
      // Check if LFB is already migrated
      lfbRequireMigration <- lastFinalizedStorage.requireMigration
      _                   <- lfbMigration.whenA(lfbRequireMigration)

      // Block DAG storage
      blockDagStorage <- BlockDagKeyValueStorage.create[F](rnodeStoreManager)

      // Casper requesting blocks cache
      casperBufferStorage <- CasperBufferKeyValueStorage.create[F](rnodeStoreManager)

      // Deploy storage
      deployStorage <- KeyValueDeployStorage[F](rnodeStoreManager)

      oracle = {
        implicit val sp = span
        SafetyOracle.cliqueOracle[F]
      }

      estimator = {
        implicit val sp = span
        Estimator[F](conf.casper.maxNumberOfParents, conf.casper.maxParentDepth)
      }
      synchronyConstraintChecker = {
        implicit val bs = blockStore
        SynchronyConstraintChecker[F]
      }
      lastFinalizedHeightConstraintChecker = {
        implicit val bs = blockStore
        LastFinalizedHeightConstraintChecker[F]
      }
      // runtime for `rnode eval`
      evalRuntime <- {
        implicit val sp = span
        for {
          store    <- rnodeStoreManager.evalStores
          runtimes <- RhoRuntime.createRuntimes[F](store)
        } yield runtimes._1
      }

      r <- {
        implicit val sp = span
        import coop.rchain.rholang.interpreter.storage._
        implicit val m: Match[F, BindPattern, ListParWithRandom] = matchListPar[F]
        // Use channels map only in block-merging (multi parents)
        val useChannelsMap = conf.casper.maxNumberOfParents > 1
        for {
          store <- rnodeStoreManager.rSpaceStores(useChannelsMap)
          spaces <- RSpace
                     .createWithReplay[F, Par, BindPattern, ListParWithRandom, TaggedContinuation](
                       store
                     )
        } yield spaces
      }
      rSpacePlay   = r._1
      rSpaceReplay = r._2
      historyRepo  = r._3

      // runtimes for on-chain execution
      onchainRuntimes <- {
        implicit val sp = span
        implicit val bs = blockStore
        implicit val bd = blockDagStorage
        for {
          runtimes <- RhoRuntime
                       .createRuntimes[F](rSpacePlay, rSpaceReplay, initRegistry = true, Seq.empty)
          (rhoRuntime, replayRhoRuntime) = runtimes
          reporter <- if (conf.apiServer.enableReporting) {
                       for {
                         // In reporting replay channels map is not needed
                         store <- rnodeStoreManager.rSpaceStores(useChannelsMap = false)
                       } yield ReportingCasper.rhoReporter(store)
                     } else
                       ReportingCasper.noop.pure[F]
        } yield (rhoRuntime, replayRhoRuntime, reporter)
      }
      (playRuntime, replayRuntime, reportingRuntime) = onchainRuntimes
      runtimeManager <- {
        implicit val sp = span
        RuntimeManager.fromRuntimes[F](playRuntime, replayRuntime, historyRepo)
      }

      // RNodeStateManager
      stateManagers <- {
        for {
          exporter           <- historyRepo.exporter
          importer           <- historyRepo.importer
          rspaceStateManager = RSpaceStateManagerImpl(exporter, importer)
          blockStateManager  = BlockStateManagerImpl(blockStore, blockDagStorage)
          rnodeStateManager  = RNodeStateManagerImpl(rspaceStateManager, blockStateManager)
        } yield (rnodeStateManager, rspaceStateManager)
      }
      (rnodeStateManager, rspaceStateManager) = stateManagers

      // Engine dynamic reference
      engineCell          <- EngineCell.init[F]
      envVars             = EnvVars.envVars[F]
      blockProcessorQueue <- Queue.unbounded[F, (Casper[F], BlockMessage)]
      // block processing state - set of items currently in processing
      blockProcessorStateRef <- Ref.of(Set.empty[BlockHash])
      blockProcessor = {
        implicit val bd = blockDagStorage
        implicit val br = blockRetriever
        implicit val cu = commUtil
        implicit val bs = blockStore
        implicit val cb = casperBufferStorage
        BlockProcessor[F]
      }

      // Proposer instance
      validatorIdentityOpt <- ValidatorIdentity.fromPrivateKeyWithLogging[F](
                               conf.casper.validatorPrivateKey
                             )
      proposer = validatorIdentityOpt.map { validatorIdentity =>
        implicit val rm         = runtimeManager
        implicit val bs         = blockStore
        implicit val bd         = blockDagStorage
        implicit val sc         = synchronyConstraintChecker
        implicit val lfhscc     = lastFinalizedHeightConstraintChecker
        implicit val sp         = span
        implicit val e          = estimator
        implicit val ds         = deployStorage
        implicit val br         = blockRetriever
        implicit val cu         = commUtil
        implicit val eb         = eventPublisher
        val dummyDeployerKeyOpt = conf.dev.deployerPrivateKey
        val dummyDeployerKey =
          if (dummyDeployerKeyOpt.isEmpty) None
          else PrivateKey(Base16.decode(dummyDeployerKeyOpt.get).get).some

        // TODO make term for dummy deploy configurable
        Proposer[F](validatorIdentity, dummyDeployerKey.map((_, "Nil")))
      }

      // Propose request is a tuple - Casper, async flag and deferred proposer result that will be resolved by proposer
      proposerQueue <- Queue.unbounded[F, (Casper[F], Boolean, Deferred[F, ProposerResult])]

      triggerProposeFOpt: Option[ProposeFunction[F]] = if (proposer.isDefined)
        Some(
          (casper: Casper[F], isAsync: Boolean) =>
            for {
              d <- Deferred[F, ProposerResult]
              _ <- proposerQueue.enqueue1((casper, isAsync, d))
              r <- d.get
            } yield r
        )
      else none[ProposeFunction[F]]

      proposerStateRefOpt <- triggerProposeFOpt.traverse(_ => Ref.of(ProposerState[F]()))

      casperLaunch = {
        implicit val bs     = blockStore
        implicit val bd     = blockDagStorage
        implicit val ec     = engineCell
        implicit val ev     = envVars
        implicit val br     = blockRetriever
        implicit val rm     = runtimeManager
        implicit val or     = oracle
        implicit val sp     = span
        implicit val lb     = lab
        implicit val rc     = rpConnections
        implicit val ra     = rpConfAsk
        implicit val eb     = eventPublisher
        implicit val sc     = synchronyConstraintChecker
        implicit val lfhscc = lastFinalizedHeightConstraintChecker
        implicit val cu     = commUtil
        implicit val es     = estimator
        implicit val ds     = deployStorage
        implicit val cbs    = casperBufferStorage
        implicit val rsm    = rspaceStateManager

        CasperLaunch.of[F](
          blockProcessorQueue,
          blockProcessorStateRef,
          if (conf.autopropose) triggerProposeFOpt else none[ProposeFunction[F]],
          conf.casper,
          !conf.protocolClient.disableLfs,
          conf.protocolServer.disableStateExporter
        )
      }
      packetHandler = {
        implicit val ec = engineCell
        CasperPacketHandler[F]
      }
      // Bypass fair dispatcher
      /*packetHandler <- {
        implicit val ec = engineCell
        implicit val rb = requestedBlocks
        implicit val sp = span
        CasperPacketHandler.fairDispatcher[F](
          conf.roundRobinDispatcher.maxPeerQueueSize,
          conf.roundRobinDispatcher.giveUpAfterSkipped,
          conf.roundRobinDispatcher.dropPeerAfterRetries
        )
      }*/
      reportingStore <- ReportStore.store[F](rnodeStoreManager)
      blockReportAPI = {
        implicit val ec = engineCell
        implicit val bs = blockStore
        implicit val or = oracle
        BlockReportAPI[F](reportingRuntime, reportingStore)
      }
      apiServers = {
        implicit val bs = blockStore
        implicit val ec = engineCell
        implicit val or = oracle
        implicit val sp = span
        implicit val sc = synchronyConstraintChecker
        implicit val lh = lastFinalizedHeightConstraintChecker
        APIServers.build[F](
          evalRuntime,
          triggerProposeFOpt,
          proposerStateRefOpt,
          conf.apiServer.maxBlocksLimit,
          conf.devMode,
          if (conf.autopropose && conf.dev.deployerPrivateKey.isDefined) triggerProposeFOpt
          else none[ProposeFunction[F]],
          blockReportAPI
        )
      }
      reportingRoutes = {
        ReportingRoutes.service[F](blockReportAPI)
      }
      casperLoop = {
        implicit val br = blockRetriever
        for {
          engine <- engineCell.read
          // Fetch dependencies from CasperBuffer
          _ <- engine.withCasper(_.fetchDependencies, ().pure[F])
          // Maintain RequestedBlocks for Casper
          _ <- BlockRetriever[F].requestAll(conf.casper.requestedBlocksTimeout)
          _ <- Time[F].sleep(conf.casper.casperLoopInterval)
        } yield ()
      }
      // Broadcast fork choice tips request if current fork choice is more then `forkChoiceStaleThreshold` minutes old.
      // For why - look at updateForkChoiceTipsIfStuck method description.
      updateForkChoiceLoop = {
        implicit val cu = commUtil
        implicit val ec = engineCell
        implicit val bs = blockStore
        for {
          _ <- Time[F].sleep(conf.casper.forkChoiceCheckIfStaleInterval)
          _ <- Running.updateForkChoiceTipsIfStuck(conf.casper.forkChoiceStaleThreshold)
        } yield ()
      }
      engineInit = engineCell.read >>= (_.init)
      runtimeCleanup = NodeRuntime.cleanup(
        rnodeStoreManager
      )
      webApi = {
        implicit val ec = engineCell
        implicit val sp = span
        implicit val or = oracle
        implicit val bs = blockStore
        new WebApiImpl[F](
          conf.apiServer.maxBlocksLimit,
          conf.devMode,
          if (conf.autopropose && conf.dev.deployerPrivateKey.isDefined) triggerProposeFOpt
          else none[ProposeFunction[F]]
        )
      }
      adminWebApi = {
        implicit val ec     = engineCell
        implicit val sp     = span
        implicit val sc     = synchronyConstraintChecker
        implicit val lfhscc = lastFinalizedHeightConstraintChecker
        new AdminWebApiImpl[F](
          triggerProposeFOpt,
          proposerStateRefOpt
        )
      }
    } yield (
      packetHandler,
      apiServers,
      casperLoop,
      updateForkChoiceLoop,
      engineInit,
      casperLaunch,
      reportingRoutes,
      webApi,
      adminWebApi,
      proposer,
      proposerQueue,
      proposerStateRefOpt,
      blockProcessor,
      blockProcessorStateRef,
      blockProcessorQueue,
      triggerProposeFOpt
    )
}