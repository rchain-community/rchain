package coop.rchain.models

import com.google.protobuf.ByteString
import coop.rchain.casper.protocol._
import coop.rchain.crypto.signatures
import coop.rchain.crypto.signatures.{Secp256k1, Signed}
import coop.rchain.models.BlockHash.BlockHash
import coop.rchain.models.Validator.Validator
import coop.rchain.models.block.StateHash
import coop.rchain.models.block.StateHash.StateHash
import org.scalacheck.Arbitrary.arbitrary
import org.scalacheck.Gen.listOfN
import org.scalacheck.util.Buildable
import org.scalacheck.{Arbitrary, Gen}

import scala.util.Random

object blockImplicits {

  // Generators
  val blockHashGen: Gen[BlockHash] = for {
    byteArray <- listOfN(BlockHash.Length, arbitrary[Byte])
  } yield ByteString.copyFrom(byteArray.toArray)

  val stateHashGen: Gen[StateHash] = for {
    byteArray <- listOfN(StateHash.Length, arbitrary[Byte])
  } yield ByteString.copyFrom(byteArray.toArray)

  val validatorGen: Gen[Validator] = for {
    byteArray <- listOfN(Validator.Length, arbitrary[Byte])
  } yield ByteString.copyFrom(byteArray.toArray)

  val bondGen: Gen[Bond] = for {
    byteArray <- listOfN(Validator.Length, arbitrary[Byte])
    validator = ByteString.copyFrom(byteArray.toArray)
    stake     <- Gen.chooseNum(1L, 1024L)
  } yield Bond(validator, stake)

  val signedDeployDataGen: Gen[Signed[DeployData]] =
    for {
      termLength <- Gen.choose(32, 1024)
      term       <- listOfN(termLength, Gen.alphaNumChar).map(_.mkString)
      timestamp  <- arbitrary[Long]
      shardId    <- arbitrary[String]
      (sec, _)   = Secp256k1.newKeyPair
      deployData = Signed(
        DeployData(
          term = term,
          timestamp = timestamp,
          phloLimit = 90000,
          phloPrice = 1L,
          validAfterBlockNumber = 0,
          shardId = shardId
        ),
        signatures.Secp256k1,
        sec
      )
    } yield deployData

  val processedDeployGen: Gen[ProcessedDeploy] =
    for {
      deployData <- signedDeployDataGen
    } yield ProcessedDeploy(
      deploy = deployData,
      cost = PCost(0L),
      deployLog = List.empty,
      isFailed = false
    )

  // Arbitrary values
  val arbitraryBlockHash: Arbitrary[BlockHash] = Arbitrary(blockHashGen)
  val arbitraryBond: Arbitrary[Bond]           = Arbitrary(bondGen)
  val arbitraryStateHash: Arbitrary[StateHash] = Arbitrary(stateHashGen)
  val arbitraryValidator: Arbitrary[Validator] = Arbitrary(validatorGen)

  val arbitraryProcessedDeploy: Arbitrary[ProcessedDeploy] = Arbitrary(
    processedDeployGen
  )
  val arbitraryProcessedDeploys: Arbitrary[Seq[ProcessedDeploy]] =
    Arbitrary.arbContainer[Seq, ProcessedDeploy](
      arbitraryProcessedDeploy,
      Buildable.buildableCanBuildFrom,
      identity
    )
  val arbitraryBlockHashes: Arbitrary[Seq[BlockHash]] =
    Arbitrary.arbContainer[Seq, BlockHash](
      arbitraryBlockHash,
      Buildable.buildableCanBuildFrom,
      identity
    )
  val arbitraryBonds: Arbitrary[Seq[Bond]] =
    Arbitrary.arbContainer[Seq, Bond](
      arbitraryBond,
      Buildable.buildableCanBuildFrom,
      identity
    )

  val blockElementsGen: Gen[List[BlockMessage]] =
    Gen.listOf(blockElementGen())

  val blockBatchesGen: Gen[List[List[BlockMessage]]] =
    Gen.listOf(blockElementsGen)

  def blockElementGen(
      setBlockNumber: Option[Long] = Some(0L),
      setSeqNumber: Option[Long] = Some(0L),
      setPreStateHash: Option[StateHash] = None,
      setPostStateHash: Option[StateHash] = None,
      setValidator: Option[Validator] = None,
      setVersion: Option[Int] = None,
      setJustifications: Option[Seq[BlockHash]] = None,
      setDeploys: Option[Seq[ProcessedDeploy]] = None,
      setSysDeploys: Option[Seq[ProcessedSystemDeploy]] = None,
      setBonds: Option[Seq[Bond]] = None,
      setShardId: Option[String] = None,
      hashF: Option[BlockMessage => BlockHash] = None
  ): Gen[BlockMessage] =
    for {
      preStatehash <- if (setPreStateHash.isEmpty)
                       arbitrary[StateHash](arbitraryStateHash)
                     else Gen.const(setPreStateHash.get)
      postStatehash <- if (setPostStateHash.isEmpty)
                        arbitrary[StateHash](arbitraryStateHash)
                      else Gen.const(setPostStateHash.get)
      justifications <- if (setJustifications.isEmpty)
                         arbitrary[Seq[BlockHash]](arbitraryBlockHashes)
                       else Gen.const(setJustifications.get)
      deploys <- if (setDeploys.isEmpty)
                  arbitrary[Seq[ProcessedDeploy]](arbitraryProcessedDeploys)
                else Gen.const(setDeploys.get)
      // 10 random validators in bonds list
      bonds <- if (setBonds.isEmpty) Gen.containerOfN[List, Bond](10, bondGen)
              else Gen.const(setBonds.get)
      // Pick random validator from bonds file
      validator <- if (setValidator.isEmpty)
                    Gen.const(
                      Random.shuffle(bonds).headOption.getOrElse(bondGen.sample.get).validator
                    )
                  else Gen.const(setValidator.get)
      version = if (setVersion.isEmpty) 1 else setVersion.get
      shardId = if (setShardId.isEmpty) "root" else setShardId.get
      block = BlockMessage(
        version = version,
        blockHash = ByteString.EMPTY,
        blockNumber = setBlockNumber.get,
        body = Body(
          state = RChainState(
            preStateHash = preStatehash,
            postStateHash = postStatehash,
            bonds = bonds.toList
          ),
          deploys = deploys.toList,
          systemDeploys = setSysDeploys.toList.flatten,
          rejectedDeploys = List.empty
        ),
        justifications = justifications.toList,
        sender = validator,
        seqNum = setSeqNumber.get,
        sig = ByteString.EMPTY,
        sigAlgorithm = "",
        shardId = shardId
      )
      blockHash <- if (hashF.isEmpty) arbitrary[BlockHash](arbitraryBlockHash)
                  else Gen.const(hashF.get(block))
      ret = block.copy(blockHash = blockHash)
    } yield ret

  def blockElementsWithParentsGen(genesis: BlockMessage): Gen[List[BlockMessage]] =
    Gen.sized { size =>
      (0 until size).foldLeft(Gen.listOfN(0, blockElementGen())) {
        case (gen, _) =>
          for {
            blocks              <- gen
            b                   <- blockElementGen(setBonds = Some(genesis.body.state.bonds))
            justifications      <- Gen.someOf(blocks)
            justificationHashes = justifications.map(_.blockHash).toList
            newBlock            = b.copy(justifications = justificationHashes)
          } yield newBlock :: blocks
      }
    }

  def blockWithNewHashesGen(blockElements: List[BlockMessage]): Gen[List[BlockMessage]] =
    Gen.listOfN(blockElements.size, blockHashGen).map { blockHashes =>
      blockElements.zip(blockHashes).map {
        case (b, hash) => b.copy(blockHash = hash)
      }
    }

  def getRandomBlock(
      setBlockNumber: Option[Long] = Some(0L),
      setSeqNumber: Option[Long] = Some(0L),
      setPreStateHash: Option[StateHash] = None,
      setPostStateHash: Option[StateHash] = None,
      setValidator: Option[Validator] = None,
      setVersion: Option[Int] = None,
      setJustifications: Option[Seq[BlockHash]] = None,
      setDeploys: Option[Seq[ProcessedDeploy]] = None,
      setSysDeploys: Option[Seq[ProcessedSystemDeploy]] = None,
      setBonds: Option[Seq[Bond]] = None,
      setShardId: Option[String] = None,
      hashF: Option[BlockMessage => BlockHash] = None
  ): BlockMessage =
    blockElementGen(
      setBlockNumber,
      setSeqNumber,
      setPreStateHash,
      setPostStateHash,
      setValidator,
      setVersion,
      setJustifications,
      setDeploys,
      setSysDeploys,
      setBonds,
      setShardId,
      hashF
    ).sample.get
}
