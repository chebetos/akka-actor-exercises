package me.chebetos.akka.blockchain.actors

import akka.actor.typed.ActorRef
import akka.actor.typed.Behavior
import akka.actor.typed.PostStop
import akka.actor.typed.javadsl.AbstractBehavior
import akka.actor.typed.javadsl.ActorContext
import akka.actor.typed.javadsl.Behaviors
import akka.actor.typed.javadsl.Receive
import me.chebetos.akka.blockchain.model.HashResult
import me.chebetos.akka.blockchain.model.PartialBlock
import mu.KotlinLogging
import java.io.Serializable
import java.io.UnsupportedEncodingException
import java.security.MessageDigest
import java.security.NoSuchAlgorithmException
import java.time.Duration
import java.time.Instant


class WorkerBehavior private constructor(context: ActorContext<Command>) : AbstractBehavior<WorkerBehavior.Command>(context) {

    data class Command(
        val block: PartialBlock,
        val startNonce: Int,
        val difficultyLevel: Int,
        val controller: ActorRef<ManagerBehavior.Command>
    ): Serializable

    companion object {
        fun create(): Behavior<Command> = Behaviors.setup {
            WorkerBehavior(it)
        }
    }

    private val logger = KotlinLogging.logger {}

    private lateinit var startInstant : Instant

    override fun createReceive(): Receive<Command> = mineBlock()

    private fun mineBlock(): Receive<Command> =
        newReceiveBuilder()
            .onAnyMessage {
                context.log.debug("received command: $it")
                startInstant = Instant.now()
                val hashResult = mineBlock(it.block, it.difficultyLevel, it.startNonce, it.startNonce + 1000)
                if (hashResult == null) {
                    context.log.debug("null")
                    return@onAnyMessage Behaviors.stopped()
                }
                context.log.info("${hashResult.nonce} : ${hashResult.hash}")
                it.controller.tell(ManagerBehavior.HashResultCommand(hashResult))
                return@onAnyMessage Behaviors.same()
            }
            .onSignal(PostStop::class.java) { signal ->
                val path = context.self.path()
                val stopInstant = Instant.now()
                if (!this::startInstant.isInitialized) {
                    startInstant = Instant.now()
                }
                val workerDuration = Duration.between(startInstant, stopInstant)
                context.log.debug("$path: I'm about to terminate!: $signal, I lived by $workerDuration")
                return@onSignal Behaviors.same()
            }
            .build()

    private fun mineBlock(block: PartialBlock, difficultyLevel: Int, startNonce: Int, endNonce: Int): HashResult? {
        logger.debug { "Mining block with parameters: block=$block, difficultyLevel=$difficultyLevel, startNonce=$startNonce, endNonce=$endNonce" }
        val target = String(CharArray(difficultyLevel)).replace("\u0000", "0")
        var hash = String(CharArray(difficultyLevel)).replace("\u0000", "X")
        var nonce = startNonce
        while (hash.substring(0, difficultyLevel) != target && nonce < endNonce) {
            nonce++
            val dataToEncode: String = block.previousHash + block.transaction.timestamp.toString() + nonce.toString() + block.transaction
            hash = calculateHash(dataToEncode)!!
        }
        return if (hash.substring(0, difficultyLevel) == target) {
            val hashResult = HashResult(
                nonce,
                hash,
                complete = true
            )
            hashResult
        } else {
            null
        }
    }

    private fun calculateHash(data: String): String? {
        return try {
            val digest = MessageDigest.getInstance("SHA-256")
            val rawHash = digest.digest(data.toByteArray(charset("UTF-8")))
            val hexString = StringBuffer()
            for (i in rawHash.indices) {
                val hex = Integer.toHexString(0xff and rawHash[i].toInt())
                if (hex.length == 1) hexString.append('0')
                hexString.append(hex)
            }
            hexString.toString()
        } catch (e: NoSuchAlgorithmException) {
            e.printStackTrace()
            null
        } catch (e: UnsupportedEncodingException) {
            e.printStackTrace()
            null
        }
    }
}