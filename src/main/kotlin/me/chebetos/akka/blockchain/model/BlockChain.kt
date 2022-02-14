package me.chebetos.akka.blockchain.model

import me.chebetos.akka.blockchain.utils.BlockChainUtils
import java.util.LinkedList

data class BlockChain(private val blocks: LinkedList<Block> = LinkedList()) {

    @Throws(BlockValidationException::class)
    fun addBlock(block: Block) {
        var lastHash = "0"
        if (blocks.size > 0) {
            lastHash = blocks.last.hash
        }
        if (lastHash != block.previousHash) {
            throw BlockValidationException()
        }
        if (!BlockChainUtils.validateBlock(block)) {
            throw BlockValidationException()
        }
        blocks.add(block)
    }

    fun printAndValidate() {
        var lastHash = "0"
        for (block in blocks) {
            println("Block " + block.transaction.id + " ")
            println(block.transaction)
            if (block.previousHash == lastHash) {
                print("Last hash matches ")
            } else {
                print("Last hash doesn't match ")
            }
            if (BlockChainUtils.validateBlock(block)) {
                println("and hash is valid")
            } else {
                println("and hash is invalid")
            }
            lastHash = block.hash
        }
    }

    fun getLastHash(): String? {
        return if (blocks.size > 0) blocks.last.hash else null
    }

    fun getSize(): Int {
        return blocks.size
    }
}
