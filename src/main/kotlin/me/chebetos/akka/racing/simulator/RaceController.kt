package me.chebetos.akka.racing.simulator

import akka.actor.typed.ActorRef
import akka.actor.typed.Behavior
import akka.actor.typed.javadsl.AbstractBehavior
import akka.actor.typed.javadsl.ActorContext
import akka.actor.typed.javadsl.Behaviors
import akka.actor.typed.javadsl.Receive
import mu.KotlinLogging
import java.io.Serializable
import java.time.Duration
import java.time.Instant
import kotlin.time.toKotlinDuration

private const val RACERS = 20
private const val TIMERKEY = "TIMER_KEY"
private val ONE_SECOND = Duration.ofSeconds(1)

class RaceController private constructor(
    context: ActorContext<Command>
) : AbstractBehavior<RaceController.Command>(context) {
    interface Command : Serializable

    data class StartCommand(val start: Long, val raceLength: Int) : Command

    private data class GetPositionCommand(val time: Long, val raceLength: Int) : Command

    data class RacerUpdateCommand(val racer: ActorRef<Racer.Command>, val position: Int) : Command

    data class RacerFinishedCommand(val racer: ActorRef<Racer.Command>, val finishedInstant: Instant) : Command

    companion object {
        fun create(): Behavior<Command> = Behaviors.setup {
            RaceController(it)
        }
    }

    private val logger = KotlinLogging.logger {}

    private val currentPositions: MutableMap<ActorRef<Racer.Command>, Int> = HashMap()
    private val finishingTimes: MutableMap<ActorRef<Racer.Command>, Instant> = HashMap()
    private val start = Instant.now()

    override fun createReceive(): Receive<Command> {
        val path = context.self.path()
        return newReceiveBuilder()
            .onMessage(StartCommand::class.java) { startCommand ->
                for (i in 1..RACERS) {
                    val racer = context.spawn(Racer.create(), "racer$i")
                    currentPositions[racer] = 0
                    logger.info { "$path: Sending StartCommand to ${racer.path()}" }
                    racer.tell(
                        Racer.StartCommand(
                            raceLength = startCommand.raceLength
                        )
                    )
                }
                return@onMessage Behaviors.withTimers { timerScheduler ->
                    val now = System.currentTimeMillis()
                    timerScheduler.startTimerAtFixedRate(TIMERKEY, GetPositionCommand(
                        time = now, raceLength = startCommand.raceLength), ONE_SECOND
                    )
                    return@withTimers Behaviors.same()
                }
            }
            .onMessage(GetPositionCommand::class.java) { positionCommand ->
                logger.debug { "$path: received $positionCommand command" }
                currentPositions.forEach { (actorRef, position) ->
//                    if (position == positionCommand.raceLength) {
//                        logger.debug { "$path: ${actorRef.path()} already achieve position: $position" }
//                        return@forEach
//                    }
                    logger.debug { "$path: sending PositionCommand command to ${actorRef.path()}, previous position: $position" }
                    actorRef.tell(Racer.PositionCommand(
                        sender = context.self
                    ))
                }
                displayRace()
                return@onMessage Behaviors.same()
            }
            .onMessage(RacerUpdateCommand::class.java) {
                currentPositions[it.racer] = it.position
                logger.debug { "$path: received ${it.position} position from ${it.racer.path()}" }
                return@onMessage Behaviors.same()
            }
            .onMessage(RacerFinishedCommand::class.java) { finishedCommand ->
                finishingTimes[finishedCommand.racer] = finishedCommand.finishedInstant
                logger.debug { "$path: received $finishedCommand" }
                if (RACERS == finishingTimes.size) {
                    return@onMessage raceCompleteMessageHandler()
                }
                return@onMessage Behaviors.same()
            }
            .build()
    }

    private fun raceCompleteMessageHandler(): Receive<Command> {
        val path = context.self.path()
        return newReceiveBuilder()
            .onMessage(GetPositionCommand::class.java) { positionCommand ->
                logger.debug { "$path: received $positionCommand command" }
                currentPositions.forEach { (actorRef, _) ->
                    logger.debug { "$path: stopping ${actorRef.path()}" }
                    context.stop(actorRef)
                }
                displayResults()
                return@onMessage Behaviors.withTimers { timerScheduler ->
                    timerScheduler.cancelAll()
                    return@withTimers Behaviors.stopped()
                }
            }
            .build()
    }

    private fun displayRace() {
        val displayLength = 160
        for (i in 0..49) println()
        val duration = Duration.between(start, Instant.now()).toKotlinDuration()
        logger.info { "Race has been running for $duration."}
        logger.info { "    ${String(CharArray(displayLength)).replace('\u0000', '=')}" }
        var i = 0
        currentPositions.forEach { (_, position) ->
            logger.info {
                "$i : " + String(CharArray(position * displayLength / 100)).replace('\u0000','*')
            }
            i++
        }
    }

    private fun displayResults() {
        logger.info{ "Results" }

        finishingTimes.values.sorted().forEach{ finishedInstant ->
            finishingTimes.forEach { (actorRef, instant) ->
                if (instant == finishedInstant) {
                    val duration = Duration.between(start, instant).toKotlinDuration()
                    logger.info { "Racer ${actorRef.path()} finished in $duration seconds." }
                }
            }
        }
    }
}