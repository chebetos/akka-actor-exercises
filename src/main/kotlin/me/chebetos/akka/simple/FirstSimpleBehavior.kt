package me.chebetos.akka.simple

import akka.actor.typed.Behavior
import akka.actor.typed.javadsl.AbstractBehavior
import akka.actor.typed.javadsl.ActorContext
import akka.actor.typed.javadsl.Behaviors
import akka.actor.typed.javadsl.Receive

class FirstSimpleBehavior private constructor(context: ActorContext<String>) : AbstractBehavior<String>(context) {
    companion object {
        fun create(): Behavior<String> = Behaviors.setup {
            FirstSimpleBehavior(it)
        }
    }
    override fun createReceive(): Receive<String> =
        newReceiveBuilder()
            .onMessageEquals("hello") {
                println("hello")
                return@onMessageEquals this
            }
            .onMessageEquals("who") {
                val path = context.self.path()
                println("My path is $path")
                return@onMessageEquals this
            }
            .onMessageEquals("child") {
                val actorRef = context.spawn(create(), "2ndActor")
                actorRef.tell("who")
                return@onMessageEquals this
            }
            .onAnyMessage {
                println("Received message: $it")
                return@onAnyMessage this
            }
            .build()

}