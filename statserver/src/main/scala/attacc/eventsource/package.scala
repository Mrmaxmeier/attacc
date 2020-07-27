package attacc

import zio.{Has, RIO, ZIO, ZLayer}
import zio.logging.Logging
import zio.interop.catz._
import fs2.Stream
import attacc.config.ConfigEnv
import fs2.concurrent.Queue
import io.lettuce.core.RedisClient
import zio.logging.log

package object eventsource {

  type ExploitEventSource = Has[ExploitEventSource.Service]

  object ExploitEventSource {
    trait Service {
      def eventStream[R <: Logging with ConfigEnv]: Stream[RIO[R, *], Event]
    }

    def eventStream[R <: ExploitEventSource with Logging with ConfigEnv]: ZIO[R, Nothing, Stream[RIO[R, *], Event]] = ZIO.access(_.get[ExploitEventSource.Service].eventStream)

    val redisSource: ZLayer[Any, Nothing, ExploitEventSource] = ZLayer.succeed(
      new Service {
        override def eventStream[R <: Logging with ConfigEnv]: Stream[RIO[R, *], Event] =
          for {
            config  <- Stream.eval(ConfigEnv.config)
            client  <- Stream(RedisClient.create(config.redisConfig.uri))
            pubSub  <- Stream(client.connectPubSub().reactive())
            _       <- Stream(pubSub.subscribe("events").subscribe())
            q       <- Stream.eval(Queue.unbounded[RIO[R, *], String])
            runtime <- Stream.eval[RIO[R, *], zio.Runtime[R]](ZIO.runtime[R])
            _ <- Stream.eval {
              ZIO.succeed {
                def enqueue(v: String): Unit =
                  runtime.unsafeRunAsync(q.enqueue1(v))(identity)
                pubSub.observeChannels().doOnNext(s => enqueue(s.getMessage)).subscribe()
              }.fork
            }
            s     <- q.dequeue
            _     <- Stream.eval(log.debug(s"received event: $s"))
            event <- Stream.eval(Events.decodeEvent(s))
          } yield event
      }
    )
  }

}
