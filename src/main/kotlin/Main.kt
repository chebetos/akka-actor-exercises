
import me.chebetos.akka.blockchain.BlockChainMiner

fun main(args: Array<String>) {
    println("Hello!")
    println("Program arguments: ${args.joinToString()}")

//    val actorSystem = ActorSystem.create(me.chebetos.akka.simple.FirstSimpleBehavior.create(), "FirstActorSystem")
//    actorSystem.tell("hello")
//    actorSystem.tell("who")
//    actorSystem.tell("child")
//    actorSystem.tell("2ndActor")
//    actorSystem.tell("Hello are you there?")
//    actorSystem.tell("This is the 2nd message?")

//    val bigPrimes = ActorSystem.create(ManagerBehavior.create(), "BigPrimes")
//    val result = AskPattern.ask(
//        bigPrimes,
//        { me: ActorRef<MutableSet<BigInteger>> -> ManagerBehavior.InstructionCommand(message = "start", sender = me)},
//        Duration.ofSeconds(60),
//        bigPrimes.scheduler()
//    )
//    result.whenComplete { reply, _ ->
//        if (reply != null) {
//            reply.forEach {
//                println(it)
//            }
//        } else {
//            println("The system doesn't response in time")
//        }
//        bigPrimes.terminate()
//    }
//    val raceControllerSystem = ActorSystem.create(RaceController.create(), "RaceControllerSystem")
//    raceControllerSystem.tell(RaceController.StartCommand(start = System.currentTimeMillis(), raceLength = 50))

    val miner = BlockChainMiner()
    miner.mineBlocks()

    // Try adding program arguments via Run/Debug configuration.
    // Learn more about running applications: https://www.jetbrains.com/help/idea/running-applications.html.
}