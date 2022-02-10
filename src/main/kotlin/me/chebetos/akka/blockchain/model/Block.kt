package me.chebetos.akka.blockchain.model

data class Block(
    val previousHash: String,
    val transaction: Transaction,
    val nonce: Int,
    val hash: String
) {
    companion object {
        fun fromPartialBlockAndHashResult(partialBlock: PartialBlock, hashResult: HashResult) = Block(
            partialBlock.previousHash, partialBlock.transaction, hashResult.nonce, hashResult.hash
        )
    }
}
