package me.chebetos.akka.blockchain.actors
import akka.actor.typed.ActorRef
import akka.actor.typed.Behavior
import akka.actor.typed.SupervisorStrategy
import akka.actor.typed.Terminated
import akka.actor.typed.javadsl.AbstractBehavior
import akka.actor.typed.javadsl.ActorContext
import akka.actor.typed.javadsl.Behaviors
import akka.actor.typed.javadsl.Receive
import akka.actor.typed.javadsl.StashBuffer
import me.chebetos.akka.blockchain.model.HashResult
import me.chebetos.akka.blockchain.model.PartialBlock
import mu.KotlinLogging
import java.io.Serializable
import java.time.Duration
import java.time.Instant

private const val WORKERS = 10

class ManagerBehavior private constructor(
    context: ActorContext<Command>,
    val stashBuffer : StashBuffer<Command>
) : AbstractBehavior<ManagerBehavior.Command>(context) {

    interface Command : Serializable

    data class MineBlockCommand(
        val block: PartialBlock,
        val sender: ActorRef<HashResult>,
        val difficultyLevel: Int,
    ) : Command

    data class HashResultCommand(
        val hashResult: HashResult,
    ) : Command

    companion object {
        fun create(): Behavior<Command> = Behaviors.withStash(10) { stash ->
            return@withStash Behaviors.setup { context ->
                return@setup ManagerBehavior(context, stash)
            }
        }
    }

    private val logger = KotlinLogging.logger {}

    private lateinit var block: PartialBlock
    private lateinit var sender: ActorRef<HashResult>
    private lateinit var startInstant : Instant

    private var difficultyLevel: Int = 5
    private var currentNonce: Int = 0
    private var currentlyMining: Boolean = false

    override fun createReceive(): Receive<Command> {
        return idleMessageHandler()
    }

    private fun idleMessageHandler(): Receive<Command> {
        val path = context.self.path()
        return newReceiveBuilder()
            .onSignal(Terminated::class.java) {
                return@onSignal Behaviors.same()
            }
            .onMessage(MineBlockCommand::class.java) { command ->
                this.logger.info { "$path: Received command from ${command.sender.path()}: $command" }

                this.startInstant = Instant.now()
                this.sender = command.sender
                this.difficultyLevel = command.difficultyLevel
                this.block = command.block
                this.currentlyMining = true
                for (i in 1..WORKERS) {
                    startNextWorker()
                }
                return@onMessage activeMessageHandler()
            }
            .build()
    }

    private fun activeMessageHandler(): Receive<Command> {
        val path = context.self.path()
        return newReceiveBuilder()
            .onSignal(Terminated::class.java) {
                startNextWorker()
                return@onSignal Behaviors.same()
            }
            .onMessage(HashResultCommand::class.java) { command ->
                context.children.forEach {
                    context.stop(it)
                }
                val stopInstant = Instant.now()
                val managerDuration = Duration.between(this.startInstant, stopInstant)
                context.log.info("$path: I'm about to terminate!, I lived by $managerDuration")

                context.log.info("$path: sending result to  ${this.sender.path()}")

                this.currentlyMining = false
                this.sender.tell(command.hashResult)
                context.log.info("$path: Returning hashResult ${command.hashResult}")
                return@onMessage stashBuffer.unstashAll(idleMessageHandler())
            }
            .onMessage(MineBlockCommand::class.java) { command ->
                this.logger.info { "$path: Enqueued command from ${command.sender.path()}: $command" }
                //context.self.tell(command)
                if (!this.stashBuffer.isFull) {
                    this.stashBuffer.stash(command)
                }
                return@onMessage Behaviors.same()
            }
            .build()
    }

    private fun startNextWorker() {
        val path = context.self.path()
        if (!currentlyMining) {
            context.log.debug("not starting new workers because currentlyMining = $currentlyMining")
            return
        }

        val workerBehavior = Behaviors.supervise(WorkerBehavior.create())
            .onFailure(SupervisorStrategy.resume())

        val worker = context.spawn(workerBehavior, "worker$currentNonce")
        logger.debug { "$path: Sending command to ${worker.path()}" }
        context.watch(worker)
        worker.tell(WorkerBehavior.Command(
            block,
            currentNonce * 1000,
            difficultyLevel,
            context.self
        ))
        currentNonce++
    }
}