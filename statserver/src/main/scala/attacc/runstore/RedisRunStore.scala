package attacc.runstore

import attacc.config.RedisConfig
import com.redis.RedisClient
import zio.macros.delegate._
import zio._

final class RedisRunStore(private val client: RedisClient) extends RunStore {
  override val runStore: RunStore.Service[Any] = new RunStore.Service[Any] {

  }
}

object RedisRunStore {
  def withRedisRunStore(config: RedisConfig) =
    enrichWithM[RunStore](
      for {
        client <- ZIO.effect(new RedisClient(config.host, config.port, config.database, if (config.secret == "") None else Some(config.secret), config.timeout))
      } yield new RedisRunStore(client)
    )
}