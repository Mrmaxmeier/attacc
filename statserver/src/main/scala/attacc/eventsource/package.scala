package attacc

import java.nio.file.{Files, Paths}

import attacc.Main.BaseEnv
import zio.{Has, Managed, RIO, ZIO, ZLayer}
import zio.interop.catz._
import zio.interop.catz.zManagedSyntax
import fs2.Stream
import attacc.config.ConfigEnv
import fs2.concurrent.Queue
import io.lettuce.core.RedisClient
import zio.logging.log

package object eventsource {

  type ExploitEventSource = Has[ExploitEventSource.Service]

  object ExploitEventSource {
    trait Service {
      def eventStream[R <: BaseEnv]: Stream[RIO[R, *], Event]
    }

    def eventStream[R <: ExploitEventSource with BaseEnv]: ZIO[R, Nothing, Stream[RIO[R, *], Event]] = ZIO.access(_.get[ExploitEventSource.Service].eventStream)

    val redisSource: ZLayer[Any, Nothing, ExploitEventSource] = ZLayer.succeed(
      new Service {
        override def eventStream[R <: BaseEnv]: Stream[RIO[R, *], Event] =
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

    val fileSource: ZLayer[Any, Nothing, ExploitEventSource] = ZLayer.succeed(
      new Service {
        override def eventStream[R <: BaseEnv]: Stream[RIO[R, *], Event] =
          Stream.eval(ZIO.runtime[R]).flatMap { implicit rts =>
            for {
              config <- Stream.eval[ZIO[R, Throwable, *], config.Config](ConfigEnv.config)
              line <- Stream
                .resource(Managed.fromAutoCloseable(ZIO.succeed(Files.newBufferedReader(Paths.get(config.appConfig.eventsFile)))).toResource)
                .flatMap {
                  Stream.unfold(_)(reader => Option(reader.readLine()).map(_ -> reader))
                }
              event <- Stream.eval(Events.decodeEvent(line))
            } yield event
          }
      }
    )
  }

}
