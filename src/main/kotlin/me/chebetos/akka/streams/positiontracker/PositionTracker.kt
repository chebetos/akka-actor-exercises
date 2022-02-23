package me.chebetos.akka.streams.positiontracker

import akka.NotUsed
import akka.actor.typed.ActorSystem
import akka.actor.typed.Behavior
import akka.actor.typed.javadsl.Behaviors
import akka.stream.javadsl.Flow
import akka.stream.javadsl.Keep
import akka.stream.javadsl.Sink
import akka.stream.javadsl.Source
import mu.KotlinLogging
import java.time.Duration
import java.time.Instant
import java.util.Random
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionStage
import kotlin.math.abs
import kotlin.math.absoluteValue
import kotlin.math.pow
import kotlin.time.toKotlinDuration


private val logger = KotlinLogging.logger {}

class PositionTracker {
    companion object {

        fun startStream(): CompletionStage<VehicleSpeed> {
            val start = Instant.now()
            val empty: Behavior<Any> = Behaviors.empty()
            val actorSystem = ActorSystem.create(empty, "actorSystem")

            val startPosition = VehiclePositionMessage(
                vehicleId = 0,
                currentDateTime = Instant.now(),
                longPosition = 0,
                latPosition = 0
            )

            val vehicleTrackingMap: MutableMap<Int, VehiclePositionMessage> = HashMap()
            for (i in 1..8) {
                vehicleTrackingMap[i] = startPosition.copy(vehicleId = i)
            }

            //source - repeat some value every 10 seconds.
            val source = Source.tick(
                Duration.ofSeconds(1),
                Duration.ofSeconds(5),
                "UPDATE"
            )

            //flow 1 - transform into the ids of each van (ie 1..8) with mapConcat
            val vanIdsFlow: Flow<String, Int, NotUsed> = Flow.of(String::class.java)
                .mapConcat {
                    logger.info { "received tick: $it" }
                    1..8
                }

            //flow 2 - get position for each van as a VPMs with a call to the lookup method (create a new instance of
            //utility functions each time). Note that this process isn't instant so should be run in parallel.
            val getVanPositionsFlow: Flow<Int, VehiclePositionMessage, NotUsed> = Flow.of(Int::class.java)
                .mapAsyncUnordered(4) { vanId ->
                    logger.debug { "retrieve position for van: $vanId" }

                    val positionRetrieverStage: CompletableFuture<VehiclePositionMessage> = CompletableFuture()
                    positionRetrieverStage.completeAsync {
                        val vehiclePositionRetriever = VehiclePositionRetriever()
                        val vehiclePosition = vehiclePositionRetriever.getVehiclePosition(vanId)
                        logger.debug { "retrieved position for van: $vanId: $vehiclePosition" }
                        return@completeAsync vehiclePosition
                    }

                    return@mapAsyncUnordered positionRetrieverStage
                }

            //flow 3 - use previous position from the map to calculate the current speed of each vehicle. Replace the
            // position in the map with the newest position and pass the current speed downstream
            val getVanSpeedFlow: Flow<VehiclePositionMessage, VehicleSpeed, NotUsed> =
                Flow.of(VehiclePositionMessage::class.java)
                    .map { vehiclePosition ->
                        val previousPosition = vehicleTrackingMap.getOrDefault(
                            vehiclePosition.vehicleId, startPosition.copy(vehicleId = vehiclePosition.vehicleId)
                        )
                        vehicleTrackingMap[vehiclePosition.vehicleId] = vehiclePosition
                        logger.debug { "calculating speed from: $previousPosition to $vehiclePosition" }
                        val vehicleSpeed = SpeedCalculator.calculateSpeed(previousPosition, vehiclePosition)
                        logger.info { "calculated speed: $vehicleSpeed" }
                        return@map vehicleSpeed
                    }
            //flow 4 - filter to only keep those values with a speed > 95
            val filterHighSpeedFlow: Flow<VehicleSpeed, VehicleSpeed, NotUsed> = Flow.of(VehicleSpeed::class.java)
                .filter { vehicleSpeed -> vehicleSpeed.speed > 95 }

            //sink - as soon as 1 value is received return it as a materialized value, and terminate the stream
            val sink: Sink<VehicleSpeed, CompletionStage<VehicleSpeed>> = Sink.head()

            val graph = source
                .via(vanIdsFlow)
                .async()
                .via(getVanPositionsFlow)
                .async()
                .via(getVanSpeedFlow)
                .via(filterHighSpeedFlow)
                .toMat(sink, Keep.right())
                .run(actorSystem)

            return graph.whenComplete { speed, throwable ->
                if (throwable != null) {
                    logger.error(throwable) { "Error processing vehicle positions" }
                }
                logger.info { "Vehicle with high speed: $speed" }

                actorSystem.terminate()
                val end = Instant.now()
                val duration = java.time.Duration.between(start, end).toKotlinDuration()
                logger.info { "It takes: $duration" }
            }
        }
    }
}

internal data class VehiclePositionMessage(
    val vehicleId: Int,
    val currentDateTime: Instant,
    val longPosition: Int,
    val latPosition: Int,
)

data class VehicleSpeed(
    val vehicleId: Int,
    val speed: Double
)

internal class SpeedCalculator {
    companion object {
        fun calculateSpeed(position1: VehiclePositionMessage, position2: VehiclePositionMessage): VehicleSpeed? {
            val longDistance = abs(position1.longPosition - position2.longPosition).toDouble()
            val latDistance = abs(position1.latPosition - position2.latPosition).toDouble()
            val distanceTravelled = (longDistance.pow(2.0) + latDistance.pow(2.0)).pow(0.5)
            val time = 1L.coerceAtLeast(
                Duration.between(position1.currentDateTime, position2.currentDateTime).seconds.absoluteValue
            )

            var speed: Double = distanceTravelled * 10 / time
            if (position2.longPosition == 0 && position2.latPosition == 0) speed = 0.0
            if (speed > 120) speed = 50.0
            return VehicleSpeed(position1.vehicleId, speed)
        }
    }
}

internal class VehiclePositionRetriever {
    fun getVehiclePosition(vehicleId: Int): VehiclePositionMessage {
        //simulate some time to get a response from the vehicle
        val r = Random()
        Thread.sleep((1000 * r.nextInt(5)).toLong())
        return VehiclePositionMessage(
            vehicleId = vehicleId,
            currentDateTime = Instant.now(),
            longPosition = r.nextInt(100),
            latPosition = r.nextInt(100)
        )
    }
}
