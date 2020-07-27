package attacc.http

import attacc.{ListFlags, ListSessions, Metrics, ObserveList, ObserveMap, ObserveValue, Query}
import cats.Monad
import cats.effect.Concurrent
import fs2.Stream
import io.circe.Json
import org.http4s.HttpRoutes
import org.http4s.dsl.Http4sDsl
import org.http4s.circe._
import Metrics.metricsEncoder

object ApiRoutes {
  def routes[F[_]: Monad: Concurrent](queryHandler: Query => Stream[F, Json], metrics: F[Metrics]): HttpRoutes[F] = {
    val dsl = Http4sDsl[F]
    import dsl._

    HttpRoutes.of {
      case GET -> Root =>
        Ok("healthy")
      case GET -> Root / "flags" =>
        Ok(queryHandler(ListFlags()))
      case GET -> Root / "sessions" =>
        Ok(queryHandler(ListSessions()))
      case GET -> Root / "value" / LongVar(id) =>
        Ok(queryHandler(ObserveValue(id)))
      case GET -> Root / "list" / LongVar(id) =>
        Ok(queryHandler(ObserveList(id)))
      case GET -> Root / "map" / LongVar(id) =>
        Ok(queryHandler(ObserveMap(id)))
      case GET -> Root / "metrics" =>
        Ok(metrics)
    }
  }
}
