package attacc

import attacc.ApplicationState.Session
import attacc.Main.ApplicationEnv
import org.http4s.{Charset, EntityEncoder}
import zio.ZIO
import io.circe.generic.auto._
import zio.interop.catz._
import io.circe.syntax._

final case class Metrics(metrics: List[Metric])
final case class Metric(name: String, value: String, timestamp: Option[Long], labels: (String, String)*)

object Metrics {
  implicit def metricsEncoder[F[_]]: EntityEncoder[F, Metrics] = EntityEncoder.stringEncoder(Charset.`UTF-8`).contramap[Metrics] { value =>
    value
      .metrics
      .map { metric =>
        val labels = metric.labels.map { case (a, b) => s"$a=${b.asJson.noSpaces}" }.mkString(",")
        s"${metric.name}${if (labels.length > 0) "{" else ""}$labels${if (labels.length > 0) "}" else ""} ${metric.value}${metric.timestamp.map(l => s" $l").getOrElse("")}"
      }
      .mkString("\n")
  }

  def collect(state: ApplicationState): ZIO[ApplicationEnv, Throwable, Metrics] = {
    def sessionKey(session: Session) = session.hostname + ':' + session.path
    for {
      sessions <- state.sessions.get.map(_._2).compile.toList
      sessionsAg = sessions.groupBy(sessionKey)
      flags <- ZIO.collectAll(sessionsAg.toList.map { case (key, sessions) => ZIO.collectAll(sessions.map(_.acceptedFlags.get)).map((key, _)) })
      runs  <- ZIO.collectAll(sessionsAg.toList.map { case (key, sessions) => ZIO.collectAll(sessions.map(_.runCounter.get)).map((key, _)) })
    } yield Metrics(
      flags.map { case (id, l)     => Metric("accepted_flags", l.sum.toString, None, "session" -> id) }
        ++ runs.map { case (id, l) => Metric("runs", l.sum.toString, None, "session"           -> id) }
    )
  }
}
