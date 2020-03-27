package attacc

import pureconfig.ConfigConvert
import pureconfig.generic.semiauto.deriveConvert

object config {

  final case class Config(
    appConfig: AppConfig,
    redisConfig: RedisConfig)

  object Config {
    implicit val convert: ConfigConvert[Config] = deriveConvert
  }

  final case class AppConfig(port: Int, host: String)

  object AppConfig {
    implicit val convert: ConfigConvert[AppConfig] = deriveConvert
  }

  final case class RedisConfig(host: String, port: Int, database: Int, secret: String, timeout: Int)

  object RedisConfig {
    implicit val convert: ConfigConvert[RedisConfig] = deriveConvert
  }
}
