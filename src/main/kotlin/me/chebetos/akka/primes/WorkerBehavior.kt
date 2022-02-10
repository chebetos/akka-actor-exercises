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
import java.util.Random

class WorkerBehavior private constructor(context: ActorContext<Command>) : AbstractBehavior<WorkerBehavior.Command>(context) {
    data class Command(val message: String, val sender: ActorRef<ManagerBehavior.Command>): Serializable

    companion object {
        fun create(): Behavior<Command> = Behaviors.setup {
            WorkerBehavior(it)
        }
    }

    private val logger = KotlinLogging.logger {}

    override fun createReceive(): Receive<Command> = handleWhenWeDontHaveAPrimeNumber()

    fun handleWhenWeDontHaveAPrimeNumber(): Receive<Command> =
        newReceiveBuilder()
            .onAnyMessage {
                val path = context.self.path()
                logger.info { "$path: calculating prime" }
                val bigInteger = BigInteger(2000, Random())
                logger.info { "$path: bigInteger: $bigInteger" }
                val prime = bigInteger.nextProbablePrime()
                logger.info { "$path: returning prime: $prime" }
                val r = Random()
                if (r.nextInt(5) < 2) {
                    it.sender.tell(
                        ManagerBehavior.ResultCommand(
                            prime = prime!!
                        )
                    )
                }
                return@onAnyMessage handleWhenAlreadyHaveAPrimeNumber(prime)
            }
            .build()

    fun handleWhenAlreadyHaveAPrimeNumber(nextProbablePrime : BigInteger): Receive<Command> =
        newReceiveBuilder()
            .onAnyMessage {
                val r = Random()
                if (r.nextInt(5) < 2) {
                    it.sender.tell(
                        ManagerBehavior.ResultCommand(
                            prime = nextProbablePrime
                        )
                    )
                }
                return@onAnyMessage Behaviors.same()
            }
            .build()
}