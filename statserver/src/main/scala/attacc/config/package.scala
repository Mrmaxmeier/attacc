package attacc

import pureconfig.error.ConfigReaderFailures
import pureconfig.generic.semiauto.deriveConvert
import pureconfig.{ConfigConvert, ConfigSource}
import zio.{Has, ZIO, ZLayer}

package object config {
  type ConfigEnv = Has[Config]
  object ConfigEnv {
    implicit val convert: ConfigConvert[Config]               = deriveConvert
    def config[E <: ConfigEnv]: ZIO[E, Nothing, Config]       = ZIO.access(_.get)
    def default: ZLayer[Any, ConfigReaderFailures, ConfigEnv] = ZLayer.fromEffect(ZIO.fromEither(ConfigSource.default.load[Config]))
  }
}
