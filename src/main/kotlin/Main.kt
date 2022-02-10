
import akka.actor.typed.ActorSystem

fun main(args: Array<String>) {
    println("Hello!")
    println("Program arguments: ${args.joinToString()}")
    val actorSystem = ActorSystem.create(me.chebetos.akka.simple.FirstSimpleBehavior.create(), "FirstActorSystem")
    actorSystem.tell("hello")
    actorSystem.tell("who")
    actorSystem.tell("child")
    actorSystem.tell("2ndActor")
    actorSystem.tell("Hello are you there?")
    actorSystem.tell("This is the 2nd message?")
}