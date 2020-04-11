package attacc

import dev.profunktor.redis4cats.effect.Log
import zio.ZIO
import zio.logging.Logging.Logging
import zio.logging.log

object Instances {
  implicit def logInstance[R <: Logging, E, A]: Log[ZIO[R, E, *]] = new Log[ZIO[R, E, *]] {
    override def info(msg: => String): ZIO[R, E, Unit]  = log.info(msg)
    override def error(msg: => String): ZIO[R, E, Unit] = log.error(msg)
  }
}
