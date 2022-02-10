package me.chebetos.akka.racing.simulator

import akka.actor.typed.ActorRef
import akka.actor.typed.Behavior
import akka.actor.typed.PostStop
import akka.actor.typed.javadsl.AbstractBehavior
import akka.actor.typed.javadsl.ActorContext
import akka.actor.typed.javadsl.Behaviors
import akka.actor.typed.javadsl.Receive
import mu.KotlinLogging
import java.io.Serializable
import java.time.Instant
import java.util.Random

class Racer private constructor(
    context: ActorContext<Command>
) : AbstractBehavior<Racer.Command>(context) {

    interface Command : Serializable

    data class StartCommand(val raceLength: Int) : Command

    data class PositionCommand(val sender: ActorRef<RaceController.Command>) : Command

    companion object {
        fun create(): Behavior<Command> = Behaviors.setup {
            Racer(it)
        }
    }

    private val logger = KotlinLogging.logger {}

    private val defaultAverageSpeed = 48.2

    private val random = Random()
    private val averageSpeedAdjustmentFactor = random.nextInt(30) - 10

    private var currentSpeed = 0.0

    private fun getMaxSpeed(): Double {
        return defaultAverageSpeed * (1 + averageSpeedAdjustmentFactor.toDouble() / 100)
    }

    private fun getDistanceMovedPerSecond(): Double {
        return currentSpeed * 1000 / 3600
    }

    private fun determineNextSpeed(currentPosition : Double, raceLength: Int) {
        currentSpeed = if (currentPosition < raceLength / 4) {
            currentSpeed + (getMaxSpeed() - currentSpeed) / 10 * random.nextDouble()
        } else {
            currentSpeed * (0.5 + random.nextDouble())
        }
        if (currentSpeed > getMaxSpeed()) currentSpeed = getMaxSpeed()
        if (currentSpeed < 5) currentSpeed = 5.0
        if (currentPosition > raceLength / 2 && currentSpeed < getMaxSpeed() / 2) {
            currentSpeed = getMaxSpeed() / 2
        }
    }

    override fun createReceive(): Receive<Command> = notYetStarted()

    private fun notYetStarted(): Receive<Command> {
        val path = context.self.path()
        return newReceiveBuilder()
            .onMessage(StartCommand::class.java) {
                return@onMessage running(currentPosition = 0.0, raceLength = it.raceLength)
            }
            .onMessage(PositionCommand::class.java) {
                it.sender.tell(RaceController.RacerUpdateCommand(racer = context.self, position = 0))
                return@onMessage Behaviors.same()
            }
            .onAnyMessage {
                logger.info{ "$path: unknown command: $it"}
                return@onAnyMessage Behaviors.same()
            }
            .build()
    }

    private fun running(currentPosition: Double, raceLength: Int): Receive<Command> {
        val path = context.self.path()
        return newReceiveBuilder()
            .onMessage(PositionCommand::class.java) {
                determineNextSpeed(currentPosition, raceLength)
                val distanceMoved = getDistanceMovedPerSecond()
                val nextPosition = currentPosition + distanceMoved
                if (nextPosition >= raceLength) {
                    it.sender.tell(RaceController.RacerUpdateCommand(racer = context.self, position = raceLength))
                    return@onMessage completed(raceLength)
                }

                it.sender.tell(RaceController.RacerUpdateCommand(racer = context.self, position = nextPosition.toInt()))
                return@onMessage running(currentPosition = nextPosition, raceLength = raceLength)
            }
            .onAnyMessage {
                logger.info{ "$path: unknown command: $it"}
                return@onAnyMessage Behaviors.same()
            }
            .build()
    }

    private fun completed(raceLength: Int): Receive<Command> {
        val path = context.self.path()
        return newReceiveBuilder()
            .onMessage(PositionCommand::class.java) {
                logger.info{ "$path: finishes"}
                it.sender.tell(RaceController.RacerUpdateCommand(racer = context.self, position = raceLength))
                it.sender.tell(RaceController.RacerFinishedCommand(racer = context.self, finishedInstant = Instant.now()))
                //return@onMessage Behaviors.ignore()
                return@onMessage waitingToStop()
            }
            .onAnyMessage {
                logger.info{ "$path: unknown command: $it"}
                return@onAnyMessage Behaviors.same()
            }
            .build()
    }

    private fun waitingToStop(): Receive<Command> {
        val path = context.self.path()
        return newReceiveBuilder()
            .onAnyMessage {
                logger.info{ "$path: waiting to stop, but received message command: $it"}
                return@onAnyMessage Behaviors.same()
            }
            .onSignal(PostStop::class.java) { signal ->
                context.log.info("$path: A-I'm about to terminate!: $signal")
                logger.info{ "$path: B-I'm about to terminate!: $signal" }
                return@onSignal Behaviors.same()
            }
            .build()
    }
}