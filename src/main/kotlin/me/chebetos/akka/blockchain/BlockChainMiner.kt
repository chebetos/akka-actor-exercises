package me.chebetos.akka.blockchain

import akka.actor.typed.ActorSystem
import akka.actor.typed.javadsl.AskPattern
import me.chebetos.akka.blockchain.actors.ManagerBehavior
import me.chebetos.akka.blockchain.actors.MiningSystemBehavior
import me.chebetos.akka.blockchain.model.Block
import me.chebetos.akka.blockchain.model.BlockChain
import me.chebetos.akka.blockchain.model.HashResult
import me.chebetos.akka.blockchain.model.PartialBlock
import me.chebetos.akka.blockchain.utils.BlocksData
import java.time.Duration
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionStage


class BlockChainMiner {
    private val difficultyLevel = 7
    private val blocks = BlockChain()
    private val start = System.currentTimeMillis()
    private lateinit var actorSystem: ActorSystem<ManagerBehavior.Command>

    private fun mineNextBlock(): CompletionStage<HashResult> {
        val nextBlockId: Int = blocks.getSize()
        if (nextBlockId >= 10) {
            val end = System.currentTimeMillis()
            actorSystem.terminate()
            blocks.printAndValidate()
            println("Time taken " + (end - start) + " ms.")
            return CompletableFuture.completedFuture(null)
        }
        val lastHash = if (nextBlockId > 0) blocks.getLastHash()!! else "0"
        println("Mining block: $nextBlockId, lastHash=$lastHash")
        val block: PartialBlock = BlocksData.getNextBlock(nextBlockId, lastHash)
        val results: CompletionStage<HashResult> = AskPattern.ask(
            actorSystem,
            { me ->
                println("${me.path()} - requesting mine block: $block")
                ManagerBehavior.MineBlockCommand(block, me, difficultyLevel)
            },
            Duration.ofSeconds(5),
            actorSystem.scheduler()
        )
        println("Waiting result for block $nextBlockId")



        return results.thenCompose { reply: HashResult ->
            println("Received result for block $nextBlockId: $reply")
            if (!reply.complete) {
                println("ERROR: No valid hash was found for a block")
            }
            val newBlock = Block.fromPartialBlockAndHashResult(block, reply)
            //try {
            blocks.addBlock(newBlock)
            println("Block added with hash : ${newBlock.hash}")
            println("Block added with nonce: ${newBlock.nonce}")
            return@thenCompose mineNextBlock()
//            } catch (e: BlockValidationException) {
//                println("ERROR: No valid hash was found for a block")
//                throw e
//            }
        }
    }

//    fun mineAnIndependentBlocks() {
//        val partialBlock = BlocksData.getNextBlock(7, "123456")
//
//        val results: CompletionStage<HashResult> = AskPattern.ask(
//            actorSystem,
//            { me -> ManagerBehavior.MineBlockCommand(partialBlock, me, difficultyLevel) },
//            Duration.ofSeconds(5),
//            actorSystem.scheduler()
//        )
//        results.whenComplete { reply: HashResult, _: Throwable ->
//            println("Received result for block 7: $reply")
//            if (!reply.complete) {
//                println("ERROR: No valid hash was found for a block")
//            }
//            val newBlock = Block.fromPartialBlockAndHashResult(partialBlock, reply)
//            try {
//                println("Received block : $newBlock")
//            } catch (e: BlockValidationException) {
//                println("ERROR: No valid hash was found for a block")
//            }
//        }
//    }

    fun mineBlocks(): BlockChain {
        actorSystem = ActorSystem.create(MiningSystemBehavior.create(), "BlockChainMiner")
        mineNextBlock()
//        mineAnIndependentBlocks()
        return blocks
    }
}