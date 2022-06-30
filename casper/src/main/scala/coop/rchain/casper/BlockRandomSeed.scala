package coop.rchain.casper

import coop.rchain.casper.protocol.BlockMessage
import coop.rchain.crypto.PublicKey
import coop.rchain.crypto.hash.Blake2b512Random
import coop.rchain.rspace.hashing.Blake2b256Hash
import coop.rchain.models.syntax._
import coop.rchain.rholang.interpreter.RhoType.Name
import scodec.bits.ByteVector
import scodec.codecs.{bytes, uint8, ulong, utf8, variableSizeBytes}
import scodec.{Codec, TransformSyntax}
import coop.rchain.models.Par

import scala.collection.compat.immutable.LazyList

final case class BlockRandomSeed private (
    shardId: String,
    blockNumber: Long,
    sender: PublicKey,
    preStateHash: Blake2b256Hash
)

object BlockRandomSeed {
  // When deploying the user deploy , the chain would execute prechargeDeploy, userDeploy and RefundDeploy in
  // sequence. The split index for the random seed is based on the index of the executions.
  val PreChargeSplitIndex: Byte           = 0.toByte
  val UserDeploySplitIndex: Byte          = 1.toByte
  val RefundSplitIndex: Byte              = 2.toByte
  val EmptyBytesBlakeHash: Blake2b256Hash = Blake2b256Hash.create(Array[Byte]())

  // using fixed pubkey to make sure unforgeable name is predictable without configuring sender
  val GenesisRandomSeedPubKey: PublicKey = PublicKey(Array[Byte]())

  // using fixed 0 to make sure unforgeable name is predictable without configuring blockNumber
  val GenesisRandomSeedBlockNumber = 0L

  def apply(
      shardId: String,
      blockNumber: Long,
      sender: PublicKey,
      preStateHash: Blake2b256Hash
  ): BlockRandomSeed = {
    assert(shardId.onlyAscii, "Shard name should contain only ASCII characters")
    new BlockRandomSeed(shardId, blockNumber, sender, preStateHash)
  }

  private val codecPublicKey: Codec[PublicKey] = variableSizeBytes(uint8, bytes)
    .xmap[PublicKey](bv => PublicKey(bv.toArray), pk => ByteVector(pk.bytes))

  val codecBlockRandomSeed: Codec[BlockRandomSeed] =
    (variableSizeBytes(uint8, utf8) :: ulong(bits = 63) ::
      codecPublicKey :: Blake2b256Hash.codecBlake2b256Hash).as[BlockRandomSeed]

  private def encode(blockRandomSeed: BlockRandomSeed): Array[Byte] =
    codecBlockRandomSeed.encode(blockRandomSeed).require.toByteArray

  def generateRandomNumber(blockRandomSeed: BlockRandomSeed): Blake2b512Random =
    Blake2b512Random(encode(blockRandomSeed))

  def generateSplitRandomNumber(blockRandomSeed: BlockRandomSeed, index: Byte): Blake2b512Random =
    generateRandomNumber(blockRandomSeed).splitByte(index)

  def generateSplitRandomNumber(
      blockRandomSeed: BlockRandomSeed,
      index: Byte,
      index2: Byte
  ): Blake2b512Random =
    generateRandomNumber(blockRandomSeed).splitByte(index).splitByte(index2)

  def fromBlock(block: BlockMessage): Blake2b512Random = {
    val seed = BlockRandomSeed(
      block.shardId,
      block.blockNumber,
      PublicKey(block.sender),
      block.preStateHash.toBlake2b256Hash
    )
    generateRandomNumber(seed)
  }

  def fromGenesis(block: BlockMessage): Blake2b512Random =
    fromGenesis(block.shardId)

  def fromGenesis(shardId: String): Blake2b512Random = {
    val seed = BlockRandomSeed(
      shardId,
      GenesisRandomSeedBlockNumber,
      GenesisRandomSeedPubKey,
      EmptyBytesBlakeHash
    )
    generateRandomNumber(seed)
  }

  def nonNegativeMergeableTagName(
      shardId: String
  ): Par = {
    // NonNegative contract is the 4th contract deployed in the genesis, start from 0. Index should be 3
    val nonNegativeContractIndex: Byte = 3
    val seed = BlockRandomSeed(
      shardId,
      GenesisRandomSeedBlockNumber,
      GenesisRandomSeedPubKey,
      EmptyBytesBlakeHash
    )
    val rand = BlockRandomSeed.generateSplitRandomNumber(
      seed,
      nonNegativeContractIndex,
      BlockRandomSeed.UserDeploySplitIndex
    )
    val unforgeableByte = Iterator.continually(rand.next()).drop(1).next()
    Name(unforgeableByte)
  }

  // This is the unforgeable name for
  // https://github.com/rchain/rchain/blob/43257ddb7b2b53cffb59a5fe1d4c8296c18b8292/casper/src/main/resources/RevVault.rho#L25
  def transferUnforgeable(shardId: String): Par = {
    // RevVault contract is the 7th contract deployed in the genesis, start from 0. Index should be 6
    val RevVaultContractDeployIndex: Byte = 6
    val seed = BlockRandomSeed(
      shardId,
      GenesisRandomSeedBlockNumber,
      GenesisRandomSeedPubKey,
      EmptyBytesBlakeHash
    )
    val rand = BlockRandomSeed.generateSplitRandomNumber(
      seed,
      RevVaultContractDeployIndex,
      BlockRandomSeed.UserDeploySplitIndex
    )
    val unfogeableBytes = Iterator.continually(rand.next()).drop(10).next()
    Name(unfogeableBytes)
  }

  def storeTokenUnforgeable(shardId: String): Par = {
    // TreeHashMap contract is the 1st contract deployed in the genesis, start from 0. Index should be 0
    val TreeHashMapContractDeployIndex: Byte = 0
    val seed = BlockRandomSeed(
      shardId,
      GenesisRandomSeedBlockNumber,
      GenesisRandomSeedPubKey,
      EmptyBytesBlakeHash
    )
    val rand = BlockRandomSeed.generateSplitRandomNumber(
      seed,
      TreeHashMapContractDeployIndex,
      BlockRandomSeed.UserDeploySplitIndex
    )
    val target = LazyList.continually(rand.next()).drop(9).head
    Name(target)
  }

  def revVaultUnforgeable(shardId: String): Par = {
    // RevVault contract is the 7th contract deployed in the genesis, start from 0. Index should be 6
    val RevVaultContractDeployIndex: Byte = 6
    val seed = BlockRandomSeed(
      shardId,
      GenesisRandomSeedBlockNumber,
      GenesisRandomSeedPubKey,
      EmptyBytesBlakeHash
    )
    val rand = BlockRandomSeed.generateSplitRandomNumber(
      seed,
      RevVaultContractDeployIndex,
      BlockRandomSeed.UserDeploySplitIndex
    )
    val unfogeableBytes = rand.next()
    Name(unfogeableBytes)
  }

}
