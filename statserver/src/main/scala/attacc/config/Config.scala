package attacc.config

import pureconfig.ConfigConvert
import pureconfig.generic.semiauto.deriveConvert

final case class Config(appConfig: AppConfig, redisConfig: RedisConfig, statsConfig: StatsConfig)
final case class AppConfig(port: Int, host: String)

object AppConfig {
  implicit val convert: ConfigConvert[AppConfig] = deriveConvert
}

final case class RedisConfig(uri: String)

object RedisConfig {
  implicit val convert: ConfigConvert[RedisConfig] = deriveConvert
}

final case class StatsConfig(lastOutputLines: Int, lastRuns: Int, lastFlags: Int)

object StatsConfig {
  implicit val convert: ConfigConvert[StatsConfig] = deriveConvert
}
