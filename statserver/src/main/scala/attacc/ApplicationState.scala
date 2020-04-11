package attacc

import java.util.UUID

import attacc.observables.{ObservableList, ObservableMap, ObservableStore, ObservableValue}
import zio.ZIO
import io.circe.generic.auto._

case class ObservableFlag(
  flag: String,
  run: UUID,
  timestamp: String,
  unique: Boolean,
  verdict: ObservableValue[Option[String]])
object ObservableFlag {
  def create(flag: String, run: UUID, timestamp: String, unique: Boolean): ZIO[ObservableStore, Throwable, ObservableFlag] =
    for {
      verdict <- ObservableStore.createValue[Option[String]](None)
    } yield ObservableFlag(flag, run, timestamp, unique, verdict)
}

case class Line(line: String, timestamp: String)

case class ObservableRun(
  id: UUID,
  session: UUID,
  interval: UUID,
  targetPkey: String,
  target: Map[String, String],
  startTime: String,
  exitCode: ObservableValue[Option[Int]],
  endTime: ObservableValue[Option[String]],
  timeouted: ObservableValue[Boolean],
  outLines: ObservableList[Line],
  errLines: ObservableList[Line],
  flags: ObservableList[ObservableFlag])
object ObservableRun {
  def create(
    id: UUID,
    session: UUID,
    interval: UUID,
    targetPkey: String,
    target: Map[String, String],
    startTime: String
  ): ZIO[ObservableStore, Throwable, ObservableRun] =
    for {
      exitCode  <- ObservableStore.createValue[Option[Int]](None)
      endTime   <- ObservableStore.createValue[Option[String]](None)
      timeouted <- ObservableStore.createValue(false)
      outLines  <- ObservableStore.createList[Line]
      errLines  <- ObservableStore.createList[Line]
      flags     <- ObservableStore.createList[ObservableFlag]
    } yield (ObservableRun(id, session, interval, targetPkey, target, startTime, exitCode, endTime, timeouted, outLines, errLines, flags))
}

case class ObservableInterval(
  id: UUID,
  session: UUID,
  startTime: String,
  endTime: ObservableValue[Option[String]],
  runs: ObservableList[UUID])
object ObservableInterval {
  def create(id: UUID, session: UUID, startTime: String): ZIO[ObservableStore, Throwable, ObservableInterval] =
    for {
      endTime <- ObservableStore.createValue[Option[String]](None)
      runs    <- ObservableStore.createList[UUID]
    } yield ObservableInterval(id, session, startTime, endTime, runs)
}

case class ObservableSession(
  id: UUID,
  hostname: String,
  path: String,
  config: Config,
  startTime: String,
  intervals: ObservableMap[UUID, ObservableInterval],
  activeInterval: ObservableValue[Option[UUID]])
object ObservableSession {
  def create(
    id: UUID,
    hostname: String,
    path: String,
    config: Config,
    startTime: String
  ): ZIO[ObservableStore, Throwable, ObservableSession] =
    for {
      intervals      <- ObservableStore.createMap[UUID, ObservableInterval]
      activeInterval <- ObservableStore.createValue[Option[UUID]](None)
    } yield ObservableSession(id, hostname, path, config, startTime, intervals, activeInterval)
}

case class ApplicationState(sessions: ObservableMap[UUID, ObservableSession], runs: ObservableMap[UUID, ObservableRun])
object ApplicationState {
  private def create: ZIO[ObservableStore, Throwable, ApplicationState] =
    for {
      sessions <- ObservableStore.createMap[UUID, ObservableSession]
      runs     <- ObservableStore.createMap[UUID, ObservableRun]
      state = ApplicationState(sessions, runs)
      obsState <- ObservableStore.createValue(state)
      _        <- obsState.name("state")
    } yield state

  def retrieveOrCreateState: ZIO[ObservableStore, Throwable, ApplicationState] =
    ObservableStore.retrieveValue[ApplicationState]("state").flatMap {
      case Some(state) => state.get
      case None        => create
    }
}
