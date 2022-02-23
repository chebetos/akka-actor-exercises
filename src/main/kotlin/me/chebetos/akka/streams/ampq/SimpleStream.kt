package me.chebetos.akka.streams.ampq

import akka.Done
import akka.NotUsed
import akka.actor.typed.ActorSystem
import akka.actor.typed.Behavior
import akka.actor.typed.javadsl.Behaviors
import akka.stream.alpakka.amqp.AmqpConnectionProvider
import akka.stream.alpakka.amqp.AmqpLocalConnectionProvider
import akka.stream.alpakka.amqp.NamedQueueSourceSettings
import akka.stream.alpakka.amqp.QueueDeclaration
import akka.stream.alpakka.amqp.ReadResult
import akka.stream.alpakka.amqp.javadsl.AmqpSource
import akka.stream.javadsl.Flow
import akka.stream.javadsl.RunnableGraph
import akka.stream.javadsl.Sink
import akka.stream.javadsl.Source
import me.chebetos.akka.streams.primes.BigPrimes
import mu.KotlinLogging
import java.time.Instant
import java.util.concurrent.CompletionStage
import kotlin.time.toKotlinDuration

private val logger = KotlinLogging.logger {}

class SimpleStream {
    companion object {

        fun startStream(): CompletionStage<List<ReadResult>>? {
            val start = Instant.now()
            val empty: Behavior<Any> = Behaviors.empty()
            val actorSystem = ActorSystem.create(empty, "actorSystem")

            val amqpConnectionProvider: AmqpConnectionProvider = AmqpLocalConnectionProvider.getInstance()

            val bufferSize = 16
            val queueName = "amqp-conn-it-test-simple-queue-" + System.currentTimeMillis();
            val queueDeclaration = QueueDeclaration.create(queueName);

            val amqpSource: Source<ReadResult, NotUsed> = AmqpSource.atMostOnceSource(
                NamedQueueSourceSettings.create(amqpConnectionProvider, queueName)
                    .withDeclaration(queueDeclaration)
                    .withAckRequired(false),
                bufferSize
            )

            val result: CompletionStage<List<ReadResult>> = amqpSource
                .take(5)
                .runWith(Sink.seq(), actorSystem)

            return result.whenComplete { results, throwable ->
                if (throwable != null) {
                    logger.error(throwable) { "Error processing queue" }
                    return@whenComplete
                }
                results.forEach { readResult -> readResult.envelope()  }
                actorSystem.terminate()
                val end = Instant.now()
                val duration = java.time.Duration.between(start, end).toKotlinDuration()
                logger.info { "It takes: $duration" }
            }

        }
    }
}
