package me.chebetos.akka.streams.ampq

import akka.Done
import akka.NotUsed
import akka.actor.typed.ActorSystem
import akka.actor.typed.Behavior
import akka.actor.typed.javadsl.Behaviors
import akka.stream.alpakka.amqp.AmqpConnectionProvider
import akka.stream.alpakka.amqp.AmqpLocalConnectionProvider
import akka.stream.alpakka.amqp.AmqpWriteSettings
import akka.stream.alpakka.amqp.NamedQueueSourceSettings
import akka.stream.alpakka.amqp.QueueDeclaration
import akka.stream.alpakka.amqp.ReadResult
import akka.stream.alpakka.amqp.WriteMessage
import akka.stream.alpakka.amqp.WriteResult
import akka.stream.alpakka.amqp.javadsl.AmqpFlow
import akka.stream.alpakka.amqp.javadsl.AmqpSource
import akka.stream.alpakka.amqp.javadsl.CommittableReadResult
import akka.stream.javadsl.Flow
import akka.stream.javadsl.Sink
import akka.stream.javadsl.Source
import akka.util.ByteString
import mu.KotlinLogging
import java.time.Duration
import java.time.Instant
import java.util.Arrays
import java.util.concurrent.CompletionStage
import kotlin.time.toKotlinDuration


private val logger = KotlinLogging.logger {}

class AmpqStream {
    companion object {
        private val empty: Behavior<Any> = Behaviors.empty()
        private val actorSystem = ActorSystem.create(empty, "actorSystem")

        private val amqpConnectionProvider: AmqpConnectionProvider = AmqpLocalConnectionProvider.getInstance()

        private const val queueName = "amqp-conn-it-test-simple-queue"
        private const val bufferSize = 16
        private val queueDeclaration: QueueDeclaration = QueueDeclaration.create(queueName)
            .withDurable(true)
            .withExclusive(false)
            .withAutoDelete(false)

        fun startStream(): CompletionStage<List<String>>? {
            val start = Instant.now()

            val amqpSource = AmqpSource.committableSource(
                NamedQueueSourceSettings.create(amqpConnectionProvider, queueName)
                    .withDeclaration(queueDeclaration)
                    .withAckRequired(true),
                bufferSize
            )

            val nackFlow = Flow.of(CommittableReadResult::class.java)
                .map { readResult ->
                    logger.debug("readResult: $readResult")
                    readResult.nack()
                    readResult.message()
                }

            val extractMessageFlow = Flow.of(ReadResult::class.java)
                .map { readResult ->
                    logger.debug("readResult: $readResult")
                    readResult.bytes().utf8String()
                }

            val result = amqpSource
                .take(5)
                .via(nackFlow)
                .via(extractMessageFlow)
                .runWith(Sink.seq(), actorSystem)

            return result.whenComplete { results, throwable ->
                if (throwable != null) {
                    logger.error(throwable) { "Error processing queue" }
                    actorSystem.terminate()
                    return@whenComplete
                }
                logger.info { "${results.size} results" }

                results.forEach { readResult ->
                    logger.info("message: $readResult")
                }
                val end = Instant.now()
                actorSystem.terminate()
                val duration = java.time.Duration.between(start, end).toKotlinDuration()
                logger.info { "It takes: $duration" }
            }

        }

        fun writeStream(): List<WriteResult> {
            val start = Instant.now()

            val settings: AmqpWriteSettings = AmqpWriteSettings.create(amqpConnectionProvider)
                .withRoutingKey(queueName)
                .withDeclaration(queueDeclaration)
                .withBufferSize(bufferSize)
                .withConfirmationTimeout(Duration.ofMillis(200))

            val amqpFlow: Flow<WriteMessage, WriteResult, CompletionStage<Done>> = AmqpFlow.createWithConfirm(settings)

            val input = listOf(
                "1-$start",
                "2-$start",
                "3-$start",
                "4-$start",
                "5-$start",
            )

            val result: List<WriteResult> = Source.from(input)
                .map { message: String ->
                    logger.info { "writing message into the queue: $message" }
                    WriteMessage.create(
                        ByteString.fromString(message)
                    )
                }
                .via<WriteResult, CompletionStage<Done>>(amqpFlow)
                .runWith(Sink.seq(), actorSystem)
                .toCompletableFuture()
                .get()

            val end = Instant.now()
            val duration = java.time.Duration.between(start, end).toKotlinDuration()
            logger.info { "It takes: $duration" }

            return result
        }

    }
}
