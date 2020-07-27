package attacc.http

import attacc.Queries
import cats.Monad
import cats.effect.Concurrent
import org.http4s._
import org.http4s.dsl.Http4sDsl
import org.http4s.server.websocket.WebSocketBuilder
import zio._
import cats.implicits._
import fs2.concurrent.Queue
import fs2.{RaiseThrowable, Stream}
import io.circe.Json
import io.circe.generic.auto._
import io.circe.parser._
import io.circe.syntax._
import org.http4s.websocket.WebSocketFrame

object WebsocketRoutes {

  final case class Request(id: String, query: Json)
  final case class Response(id: String, response: Json)
  final case class Error(id: Option[String], error: String)

  def routes[F[_]: Monad: Concurrent: RaiseThrowable](queryHandler: attacc.Query => Stream[F, Json]): HttpRoutes[F] = {
    val dsl = Http4sDsl[F]
    import dsl._

    HttpRoutes.of {
      case GET -> Root =>
        for {
          queue <- Queue.unbounded[F, Json]
          response <- {
            def process(in: Stream[F, WebSocketFrame]): Stream[F, Unit] = {
              val handled = for {
                frame   <- in
                request <- Stream.fromEither(frame.data.decodeUtf8.flatMap(decode[Request]))
                query   <- Stream.fromEither(Queries.fromJson(request.query))
                result <- queryHandler(query)
                  .map(r => Response(request.id, r).asJson)
              } yield result
              handled
                .handleErrorWith(e => Stream(Error(None, e.getMessage).asJson))
                .through(queue.enqueue)
            }

            val out = queue.dequeue.map(v => WebSocketFrame.Text(v.noSpaces))

            WebSocketBuilder[F].build(out, process)
          }
        } yield response
    }
  }
}
