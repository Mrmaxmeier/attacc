package attacc

import attacc.config.ConfigEnv
import attacc.eventsource.ExploitEventSource
import attacc.observables.{InMemoryStore, ObservableStore}
import zio.console.putStrLn
import zio._
import zio.interop.catz._
import zio.logging.Logging.Logging
import zio.logging.log
import zio.logging.slf4j.Slf4jLogger

object Main extends App {

  val baseLayer        = Slf4jLogger.make((_, msg) => msg) ++ ConfigEnv.default
  val applicationLayer = ExploitEventSource.redisSource ++ InMemoryStore.inMemoryStore
  val customLayers     = baseLayer ++ applicationLayer

  type ApplicationEnv = ZEnv with Logging with ConfigEnv with ExploitEventSource with ObservableStore

  override def run(args: List[String]): ZIO[ZEnv, Nothing, Int] =
    (for {
      _      <- log.info("Creating application state")
      state  <- ApplicationState.retrieveOrCreateState
      events <- ExploitEventSource.eventStream[ApplicationEnv]
      _      <- log.info("Starting event loop")
      _ <- events
        .evalMap(EventHandling.handle(state))
        .compile
        .drain
    } yield ())
      .provideCustomLayer(customLayers)
      .foldM(
        err => putStrLn(s"Execution failed with: $err").as(1),
        _ => ZIO.succeed(0)
      )
  /*
  def runHttp[R <: Clock](httpApp: HttpApp[RIO[R, *]], config: AppConfig): ZIO[R, Throwable, Unit] = {
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
 */
}
