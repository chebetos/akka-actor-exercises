package me.chebetos.akka.primes
import akka.actor.typed.ActorRef
import akka.actor.typed.Behavior
import akka.actor.typed.javadsl.AbstractBehavior
import akka.actor.typed.javadsl.ActorContext
import akka.actor.typed.javadsl.Behaviors
import akka.actor.typed.javadsl.Receive
import mu.KotlinLogging
import java.io.Serializable
import java.math.BigInteger
import java.time.Duration
import java.util.TreeSet

private const val WORKERS = 20

class ManagerBehavior private constructor(
    context: ActorContext<Command>
) : AbstractBehavior<ManagerBehavior.Command>(context) {
    interface Command : Serializable

    data class InstructionCommand(
        val message: String,
        val sender: ActorRef<MutableSet<BigInteger>>
    ) : Command

    data class ResultCommand(val prime: BigInteger) : Command

    private data class NoResponseReceived(val worker: ActorRef<WorkerBehavior.Command>) : Command

    companion object {
        fun create(): Behavior<Command> = Behaviors.setup {
            ManagerBehavior(it)
        }
    }

    private val logger = KotlinLogging.logger {}

    private val primes: MutableSet<BigInteger> = TreeSet()

    private lateinit var sender: ActorRef<MutableSet<BigInteger>>

    override fun createReceive(): Receive<Command> =
        newReceiveBuilder()
            .onMessage(InstructionCommand::class.java) {
                val path = context.self.path()
                when (it.message) {
                    "start" -> {
                        for (i in 1..WORKERS) {
                            val actorRef = context.spawn(WorkerBehavior.create(), "worker${i}")
                            askWorkerForAPrime(actorRef)
                        }
                        sender = it.sender
                    }
                    else -> logger.info{ "$path: unknown message: ${it.message }"}
                }
                return@onMessage Behaviors.same()
            }
            .onMessage(ResultCommand::class.java) { command ->
                val path = context.self.path()
                primes.add(command.prime)
                logger.info { "$path: I received ${primes.size} prime numbers" }
                if (WORKERS == primes.size) {
                    sender.tell(primes)
                }
                return@onMessage Behaviors.same()
            }
            .onMessage(NoResponseReceived::class.java) { command ->
                val path = context.self.path()
                logger.info { "$path: Retrying with worker ${command.worker.path()}" }
                askWorkerForAPrime(command.worker)
                return@onMessage Behaviors.same()
            }
            .onAnyMessage {
                val path = context.self.path()
                logger.info{ "$path: unknown command: $it"}
                return@onAnyMessage Behaviors.same()
            }
            .build()

    private fun askWorkerForAPrime(worker : ActorRef<WorkerBehavior.Command>) {
        val path = context.self.path()
        logger.info { "$path: Sending command to ${worker.path()}" }
        context.ask(
            Command::class.java,
            worker,
            Duration.ofSeconds(5),
            { me -> WorkerBehavior.Command(message = "start", sender = me) },
            { response, _ ->
                return@ask if (response != null) {
                    logger.info { "Worker: ${worker.path()} response: $response" }
                    response
                } else {
                    logger.info { "Worker: ${worker.path()} failed to response" }
                    NoResponseReceived(worker = worker)
                }
            }
        )
    }
}