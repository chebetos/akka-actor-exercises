package me.chebetos.akka.blockchain.actors
import akka.actor.typed.ActorRef
import akka.actor.typed.Behavior
import akka.actor.typed.SupervisorStrategy
import akka.actor.typed.javadsl.AbstractBehavior
import akka.actor.typed.javadsl.ActorContext
import akka.actor.typed.javadsl.Behaviors
import akka.actor.typed.javadsl.PoolRouter
import akka.actor.typed.javadsl.Receive
import akka.actor.typed.javadsl.Routers
import mu.KotlinLogging

class MiningSystemBehavior private constructor(
    context: ActorContext<ManagerBehavior.Command>
) : AbstractBehavior<ManagerBehavior.Command>(context) {

    companion object {
        fun create(): Behavior<ManagerBehavior.Command> = Behaviors.setup {
            return@setup MiningSystemBehavior(it)
        }
    }

    private val logger = KotlinLogging.logger {}
    private val managerPoolRouter : PoolRouter<ManagerBehavior.Command> =  Routers.pool(3,
        Behaviors.supervise(ManagerBehavior.create()).onFailure(SupervisorStrategy.restart())
    )
    private val managers: ActorRef<ManagerBehavior.Command> = context.spawn(managerPoolRouter, "managerPool")

    override fun createReceive(): Receive<ManagerBehavior.Command> {
        val path = context.self.path()
        return newReceiveBuilder()
            .onAnyMessage { command ->
                this.logger.info { "$path: Received command: $command" }
                this.managers.tell(command)
                return@onAnyMessage Behaviors.same()
            }
            .build()
    }
}
