package attacc

import attacc.config.{AppConfig, Config}
import cats.effect.ExitCode
import attacc.http.StatsService
import attacc.log.Log
import org.http4s.HttpApp
import org.http4s._
import org.http4s.implicits._
import org.http4s.server.Router
import org.http4s.server.blaze.BlazeServerBuilder
import org.http4s.server.middleware.CORS
import pureconfig.ConfigSource
import attacc.runstore.RedisRunStore.withRedisRunStore
import attacc.log.Slf4jLogger.withSlf4jLogger
import attacc.runstore.RunStore
import zio.clock.Clock
import zio.console.putStrLn
import zio._
import zio.interop.catz._
import zio.macros.delegate._

object Main extends ManagedApp {

  type AppEnvironment = ZEnv
    with RunStore
    with Log
  type AppTask[A] = RIO[AppEnvironment, A]

  override def run(args: List[String]): ZManaged[ZEnv, Nothing, Int] =
    (for {
      cfg <- ZIO.fromEither(ConfigSource.default.load[Config]).toManaged_

      httpApp = Router[AppTask](
        "/api/" -> StatsService.routes()
      ).orNotFound

      _ <- (ZIO.environment[ZEnv] @@
            withRedisRunStore(cfg.redisConfig) @@
            withSlf4jLogger >>>
            runHttp(httpApp, cfg.appConfig)).toManaged_

    } yield ())
      .foldM(
        err => putStrLn(s"Execution failed with: $err").as(1).toManaged_,
        _ => ZManaged.succeed(0)
      )

  def runHttp[R <: Clock](
    httpApp: HttpApp[RIO[R, *]],
    config: AppConfig
  ): ZIO[R, Throwable, Unit] = {
    type Task[A] = RIO[R, A]
    ZIO.runtime[R].flatMap { implicit rts =>
      BlazeServerBuilder[Task]
        .bindHttp(config.port, config.host)
        .withHttpApp(CORS(httpApp))
        .serve
        .compile[Task, Task, ExitCode]
        .drain
    }
  }
}
