package attacc

import dev.profunktor.redis4cats.connection.{RedisClient, RedisURI}
import dev.profunktor.redis4cats.domain.{RedisChannel, RedisCodec}
import dev.profunktor.redis4cats.interpreter.pubsub.PubSub
import zio.{Has, RIO, ZIO, ZLayer}
import zio.logging.Logging.Logging
import zio.interop.catz._
import fs2.Stream
import Instances._
import attacc.config.ConfigEnv
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
            uri     <- Stream.eval(RedisURI.make[RIO[R, *]](config.redisConfig.uri)(monadErrorInstance))
            client  <- Stream.resource(RedisClient[RIO[R, *]](uri))
            runtime <- Stream.eval[RIO[R, *], zio.Runtime[R]](ZIO.runtime[R])
            sub     <- PubSub.mkSubscriberConnection[RIO[R, *], String, String](client, RedisCodec.Utf8)(taskEffectInstance(runtime), zioContextShift, logInstance)
            s       <- sub.subscribe(RedisChannel("events"))
            _       <- Stream.eval(log.debug(s"received event: $s"))
            event   <- Stream.eval(Events.decodeEvent(s))
          } yield event
      }
    )
  }

}
