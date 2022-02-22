package me.chebetos.akka.streams.primes

import akka.Done
import akka.NotUsed
import akka.actor.typed.ActorSystem
import akka.actor.typed.Behavior
import akka.actor.typed.javadsl.Behaviors
import akka.stream.Attributes
import akka.stream.OverflowStrategy
import akka.stream.javadsl.Flow
import akka.stream.javadsl.Keep
import akka.stream.javadsl.Sink
import akka.stream.javadsl.Source
import mu.KotlinLogging
import java.math.BigInteger
import java.time.Instant
import java.util.Collections
import java.util.Random
import java.util.concurrent.CompletionStage
import kotlin.text.Typography.prime
import kotlin.time.Duration
import kotlin.time.toKotlinDuration


class BigPrimes {
    companion object {
        private val logger = KotlinLogging.logger {}

        fun startStream(): CompletionStage<Done>? {
            val start = Instant.now()
            val empty: Behavior<Any> = Behaviors.empty()
            val actorSystem = ActorSystem.create(empty, "actorSystem")

            val source: Source<Int, NotUsed> = Source.range(1, 100)

            val random = Random()

            val flow: Flow<Int, Pair<Int, BigInteger>, NotUsed>? = Flow.of(Int::class.java)
                .log("From Source:")
                .map {
                    val biginteger = BigInteger(2000, random)
                    logger.info { "$it: BigInteger:\t${biginteger.toString().take(5)}" }
                    return@map Pair(it,  biginteger)
                }
                .log("Prime:")

            val primeGenerator: Flow<Pair<Int, BigInteger>, BigInteger, NotUsed> =  Flow.fromFunction {
                val id: Int = it.first as Int
                val bigInteger: BigInteger = it.second as BigInteger
                val prime = bigInteger.nextProbablePrime()
                logger.info { "$id:\t${bigInteger.toString().take(5)} -> Prime:\t${prime.toString().take(5)}" }
                //actorSystem.log().debug("Found prime from $it : $prime")
                return@fromFunction prime
                }

            val groupedFlow = Flow.of(BigInteger::class.java)
                .log("BigInteger: ")
                .grouped(100)
                .log("Grouped: ")
                .map { value: List<BigInteger> ->
                    val newList: List<BigInteger> = ArrayList(value)
                    Collections.sort(newList)
                    return@map newList
                }
                .log("New List:")

            val sink: Sink<List<BigInteger>, CompletionStage<Done>>? =
                Sink.foreach { primeList -> primeList.forEach { prime -> logger.info { prime.toString().take(3) } } }

            val graph = source
                .via(flow)
                .buffer(16, OverflowStrategy.backpressure())
                .async()
                .via(primeGenerator.addAttributes(Attributes.inputBuffer(16, 32)))
                .async()
                .via(groupedFlow)
                .toMat(sink, Keep.right())
                .run(actorSystem)

            return graph.whenComplete { _, _ ->
                actorSystem.terminate()
                val end = Instant.now()
                val duration = java.time.Duration.between(start, end).toKotlinDuration()
                logger.info { "It takes: $duration" }
            }
        }
    }
}
