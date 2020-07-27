package attacc

import attacc.config.{AppConfig, ConfigEnv}
import attacc.eventsource.ExploitEventSource
import attacc.http.{ApiRoutes, WebsocketRoutes}
import attacc.observables.{InMemoryStore, ObservableStore}
import zio.ExitCode
import io.circe.{Json, JsonObject}
import org.http4s.server.blaze.BlazeServerBuilder
import org.http4s.server.middleware.CORS
import zio.console.putStrLn
import zio._
import zio.clock.Clock
import zio.interop.catz._
import zio.logging.Logging
import zio.logging.log
import zio.logging.slf4j.Slf4jLogger
import io.circe.syntax._
import scala.concurrent.ExecutionContext.Implicits.global
import org.http4s.implicits._
import org.http4s.server.Router

object Main extends App {

  val baseLayer        = Slf4jLogger.make((_, msg) => msg) ++ ConfigEnv.default
  val applicationLayer = ExploitEventSource.redisSource ++ InMemoryStore.inMemoryStore
  val customLayers     = baseLayer ++ applicationLayer

  type BaseEnv        = ZEnv with Logging with ConfigEnv
  type ApplicationEnv = BaseEnv with ExploitEventSource with ObservableStore

  override def run(args: List[String]): URIO[ZEnv, ExitCode] =
    (for {
      _      <- log.info("Creating application state")
      state  <- ApplicationState.retrieveOrCreateState
      events <- ExploitEventSource.eventStream[ApplicationEnv]
      config <- ConfigEnv.config[ApplicationEnv]
      _      <- log.info("Starting webserver")
      handler = (query: Query) => QueryHandling.handle(state)(query).handleErrorWith(t => fs2.Stream(JsonObject(("error", t.getMessage.asJson)).asJson))
      http <- runHttp(config.appConfig, handler, Metrics.collect(state)).fork
      _    <- log.info("Starting event loop")
      eventLoop <- events
        .evalMap(EventHandling.handle(state))
        .compile
        .drain
        .fork
      _ <- http.zip(eventLoop).await
    } yield ())
      .provideCustomLayer(customLayers)
      .foldM(
        err => putStrLn(s"Execution failed with: $err").as(ExitCode(1)),
        _ => ZIO.succeed(ExitCode(0))
      )

  def runHttp[R <: Clock](config: AppConfig, handler: Query => fs2.Stream[ZIO[R, Throwable, *], Json], metrics: ZIO[R, Throwable, Metrics]): ZIO[R, Throwable, Unit] = {
    type Task[A] = RIO[R, A]
    ZIO.runtime[R].flatMap { implicit rts =>
      BlazeServerBuilder[Task](global)
        .bindHttp(config.port, config.host)
        .withHttpApp(CORS(Router("api" -> ApiRoutes.routes(handler, metrics), "ws" -> WebsocketRoutes.routes(handler)).orNotFound))
        .serve
        .compile[Task, Task, cats.effect.ExitCode]
        .drain
    }
  }

}
