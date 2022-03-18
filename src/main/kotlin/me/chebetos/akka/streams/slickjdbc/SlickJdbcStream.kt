package me.chebetos.akka.streams.slickjdbc

import akka.Done
import akka.actor.typed.ActorSystem
import akka.actor.typed.javadsl.Behaviors
import akka.stream.alpakka.slick.javadsl.Slick
import akka.stream.alpakka.slick.javadsl.SlickRow
import akka.stream.alpakka.slick.javadsl.SlickSession
import akka.stream.javadsl.Sink
import akka.stream.javadsl.Source
import mu.KotlinLogging
import java.sql.Connection
import java.sql.PreparedStatement
import java.time.Instant
import java.util.UUID
import java.util.concurrent.CompletionStage
import java.util.concurrent.TimeUnit
import kotlin.time.toKotlinDuration

private val logger = KotlinLogging.logger {}

private const val INSERT_USER_SENTENCE = "INSERT INTO ALPAKKA_SLICK_JAVADSL_TEST_USERS VALUES (?, ?)"

class SlickJdbcStream(private val session: SlickSession, private val actorSystem: ActorSystem<Any>) {

    companion object {

        private data class User(val id: Int, val name: String)

        private fun executeStatement(statement: String, session: SlickSession, system: ActorSystem<*>) {
            try {
                Source.single(statement)
                    .runWith(Slick.sink(session), system)
                    .toCompletableFuture()[3, TimeUnit.SECONDS]
            } catch (e: Exception) {
                throw RuntimeException(e)
            }
        }

        fun executeInsertAndRead() {
            val slickActorSystem = ActorSystem.create(Behaviors.empty<Any>(), "slickActorSystem")
            val session: SlickSession = SlickSession.forConfig("slick-h2-mem")
            slickActorSystem.classicSystem().registerOnTermination(session::close);

            executeStatement(
                "CREATE TABLE ALPAKKA_SLICK_JAVADSL_TEST_USERS(ID INTEGER, NAME VARCHAR(50))",
                session,
                slickActorSystem
            );

            val stream = SlickJdbcStream(session, slickActorSystem)
            stream.insertUsers()
            stream.readStream()
            slickActorSystem.terminate()
        }
    }

    fun insertUsers() {
        val start = Instant.now()

        val users = listOf(
            User(1, UUID.randomUUID().toString()),
            User(2, UUID.randomUUID().toString()),
            User(3, UUID.randomUUID().toString()),
            User(4, UUID.randomUUID().toString()),
            User(5, UUID.randomUUID().toString()),
        )

        val usersSource = Source.from(users)

        // add an optional second argument to specify the parallelism factor (int)
        val slickInsertionSink: Sink<User, CompletionStage<Done>> = Slick.sink(session) { user, connection: Connection ->
            val statement: PreparedStatement = connection.prepareStatement(
                INSERT_USER_SENTENCE
            )
            logger.info { "inserting: $user" }
            statement.setInt(1, user.id);
            statement.setString(2, user.name);
            return@sink statement
        }

        usersSource
            .runWith(slickInsertionSink, actorSystem)
            .toCompletableFuture()
            .get()

        val end = Instant.now()
        val duration = java.time.Duration.between(start, end).toKotlinDuration()
        logger.info { "It takes: $duration" }
    }

    fun readStream() {
        val start = Instant.now()

        val query = "SELECT ID, NAME FROM ALPAKKA_SLICK_JAVADSL_TEST_USERS"
        Slick.source(session, query) { row: SlickRow ->
            val user = User(row.nextInt(), row.nextString())
            logger.info { "read user from db: $user" }
            return@source user
        }
            .log("user")
            .runWith(Sink.ignore(), actorSystem)
            .toCompletableFuture()
            .get()

        val end = Instant.now()
        val duration = java.time.Duration.between(start, end).toKotlinDuration()
        logger.info { "It takes: $duration" }
    }
}