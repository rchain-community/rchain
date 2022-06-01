package coop.rchain.casper.util

import cats.syntax.all._
import coop.rchain.casper.helper.TestNode
import coop.rchain.casper.helper.TestNode.Effect
import coop.rchain.models.BlockHash.BlockHash
import coop.rchain.models.blockImplicits._
import coop.rchain.p2p.EffectsTestInstances.LogicalTime
import coop.rchain.shared.scalatestcontrib._
import monix.execution.Scheduler.Implicits.global
import org.scalatest._
import org.scalatestplus.scalacheck.ScalaCheckDrivenPropertyChecks

class ProtoUtilTest extends FlatSpec with Matchers with ScalaCheckDrivenPropertyChecks {

  "dependenciesHashesOf" should "return hashes of all justifications and parents of a block" in {
    forAll(blockElementGen()) { blockElement =>
      val result = ProtoUtil.dependenciesHashesOf(blockElement)
      val justificationsHashes = blockElement.justifications.map(
        _.latestBlockHash
      )
      val parentsHashes = blockElement.header.parentsHashList
      result should contain allElementsOf (justificationsHashes)
      result should contain allElementsOf (parentsHashes)
      result should contain theSameElementsAs ((justificationsHashes ++ parentsHashes).toSet)
    }
  }

  import GenesisBuilder._

  implicit val timeEff = new LogicalTime[Effect]

  val genesis = buildGenesis()

  "unseenBlockHashes" should "return empty for a single block dag" in effectTest {
    TestNode.standaloneEff(genesis).use { node =>
      import node.{blockDagStorage, blockStore}
      for {
        signedBlock <- ConstructDeploy.basicDeployData[Effect](
                        0,
                        shardId = genesis.genesisBlock.shardId
                      ) >>= (node.addBlock(_))
        dag               <- node.blockDagStorage.getRepresentation
        unseenBlockHashes <- ProtoUtil.unseenBlockHashes[Effect](dag, signedBlock)
        _                 = unseenBlockHashes should be(Set.empty[BlockHash])
      } yield ()
    }
  }

  it should "return all but the first block when passed the first block in a chain" in effectTest {
    TestNode.standaloneEff(genesis).use { node =>
      import node.{blockDagStorage, blockStore}
      val shardId = this.genesis.genesisBlock.shardId

      for {
        block0 <- ConstructDeploy
                   .basicDeployData[Effect](0, shardId = shardId) >>= (node
                   .addBlock(_))
        block1 <- ConstructDeploy
                   .basicDeployData[Effect](1, shardId = shardId) >>= (node.addBlock(_))
        dag               <- node.blockDagStorage.getRepresentation
        unseenBlockHashes <- ProtoUtil.unseenBlockHashes[Effect](dag, block0)
        _                 = unseenBlockHashes should be(Set(block1.blockHash))
      } yield ()
    }
  }
}
