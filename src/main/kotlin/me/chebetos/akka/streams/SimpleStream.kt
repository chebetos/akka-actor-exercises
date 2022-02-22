package me.chebetos.akka.streams

import akka.Done
import akka.NotUsed
import akka.actor.typed.ActorSystem
import akka.actor.typed.Behavior
import akka.actor.typed.javadsl.Behaviors
import akka.stream.javadsl.Flow
import akka.stream.javadsl.RunnableGraph
import akka.stream.javadsl.Sink
import akka.stream.javadsl.Source
import java.util.concurrent.CompletionStage

class SimpleStream {
    companion object {
        fun startStream() {
            val source: Source<Int, NotUsed> = Source.range(1, 10)
            val flow: Flow<Int, String, NotUsed> = Flow.of(Int::class.java)
                .map { "The next value is $it" }
            val sink: Sink<String, CompletionStage<Done>>? = Sink.foreach { it: String -> println(it)  }

            val graph: RunnableGraph<NotUsed> = source
                .via(flow)
                .to(sink)

            val empty: Behavior<Any> = Behaviors.empty()
            val actorSystem = ActorSystem.create(empty, "actorSystem")

            graph.run(actorSystem)

        }
    }
}
