package attacc.http

import com.github.ghik.silencer.silent
import com.github.ghik.silencer.silent
import org.http4s._
import org.http4s.dsl.Http4sDsl
import zio._
import zio.interop.catz._

object StatsService {

  @silent("unreachable") // https://github.com/scala/bug/issues/11457
  def routes[R](): HttpRoutes[RIO[R, *]] = {
    type StatsTask[A] = RIO[R, A]

    val dsl: Http4sDsl[StatsTask] = Http4sDsl[StatsTask]
    import dsl._

    HttpRoutes.of[StatsTask] {

      case GET -> Root =>
        Ok("Ehlo")
    }
  }
}
