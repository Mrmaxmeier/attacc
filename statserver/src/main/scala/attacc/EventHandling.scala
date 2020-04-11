package attacc

import attacc.Main.ApplicationEnv
import zio.ZIO
import zio.logging.log

object EventHandling {
  def handle(state: ApplicationState)(event: Event): ZIO[ApplicationEnv, Throwable, Unit] =
    for {
      _ <- log.info(s"memes: $event, $state")
    } yield ()
}
