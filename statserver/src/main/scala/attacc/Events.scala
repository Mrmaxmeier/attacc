package attacc

import java.util.UUID

import io.circe._
import io.circe.generic.extras.auto._
import io.circe.generic.extras.semiauto._
import io.circe.syntax._
import io.circe.parser._
import io.circe.generic.extras.Configuration
import zio.ZIO

final case class Run(id: UUID, key: String, target: Map[String, String])

final case class Config(
  command: Array[String],
  interval: Double,
  timeout: Double,
  concurrency: Long,
  targets: Array[Map[String, Map[String, Json]]])

sealed trait EventPayload
final case class SessionAnnouncement(hostname: String, path: String, config: Config) extends EventPayload
final case class IntervalStart()                                                     extends EventPayload
final case class IntervalEnd()                                                       extends EventPayload
final case class RunStart(run: Run)                                                  extends EventPayload
final case class RunTimeout(run: Run)                                                extends EventPayload
final case class RunExit(run: Run, exitCode: Int)                                    extends EventPayload
final case class StdoutLine(run: Run, line: String)                                  extends EventPayload
final case class StderrLine(run: Run, line: String)                                  extends EventPayload
final case class FlagMatch(run: Run, flag: String, isUnique: Boolean)                extends EventPayload
final case class FlagVerdict(run: Run, flag: String, verdict: String)                extends EventPayload

final case class Event(sessionId: UUID, timestamp: String, payload: EventPayload)

object Events {
  implicit val config: Configuration = Configuration.default.withSnakeCaseMemberNames

  private def wrapPayload(t: String)                                        = JsonObject(("t", t.asJson)).asJson
  private def wrapPayload[P](t: String, c: P)(implicit encoder: Encoder[P]) = JsonObject(("t", t.asJson), ("c", c.asJson)).asJson

  implicit val encodeEventPayload: Encoder[EventPayload] = Encoder.instance {
    case e: SessionAnnouncement => wrapPayload("SessionAnnouncement", e)
    case _: IntervalStart       => wrapPayload("IntervalStart")
    case _: IntervalEnd         => wrapPayload("IntervalEnd")
    case e: RunStart            => wrapPayload("RunStart", e.run)
    case e: RunTimeout          => wrapPayload("RunTimeout", e.run)
    case e: RunExit             => wrapPayload("RunExit", e)
    case e: StdoutLine          => wrapPayload("StdoutLine", e)
    case e: StderrLine          => wrapPayload("StderrLine", e)
    case e: FlagMatch           => wrapPayload("FlagMatch", e)
    case e: FlagVerdict         => wrapPayload("FlagVerdict", e)
  }

  implicit val decodeEventPayload: Decoder[EventPayload] = Decoder.instance { cursor =>
    val error = DecodingFailure(_, cursor.history)
    for {
      w <- cursor.as[JsonObject]
      t <- w("t").toRight(error("field \"t\" required on wrapped EventPayload")).flatMap(_.as[String])
      requireC   = (f: Json => Decoder.Result[EventPayload]) => w("c").toRight(error(s"field c required on wrapped $t")).flatMap(f)
      requireNoC = (e: EventPayload) => Either.cond(!w.contains("c"), e, error(s"field c not allowed on wrapped $t"))
      result <- t match {
        case "SessionAnnouncement" => requireC(_.as[SessionAnnouncement])
        case "IntervalStart"       => requireNoC(IntervalStart())
        case "IntervalEnd"         => requireNoC(IntervalEnd())
        case "RunStart"            => requireC(_.as[Run].map(RunStart))
        case "RunTimeout"          => requireC(_.as[Run].map(RunTimeout))
        case "RunExit"             => requireC(_.as[RunExit])
        case "StdoutLine"          => requireC(_.as[StdoutLine])
        case "StderrLine"          => requireC(_.as[StderrLine])
        case "FlagMatch"           => requireC(_.as[FlagMatch])
        case "FlagVerdict"         => requireC(_.as[FlagVerdict])
      }
    } yield result
  }

  implicit val decodeEvent: Decoder[Event] = deriveConfiguredDecoder[Event]
  implicit val encodeEvent: Encoder[Event] = deriveConfiguredEncoder[Event]

  def decodeEvent(s: String): ZIO[Any, Error, Event] = ZIO.fromEither(decode[Event](s))
}
