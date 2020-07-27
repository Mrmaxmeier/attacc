package attacc

import java.time.LocalDateTime
import java.util.UUID

import attacc.ApplicationState.Session
import attacc.Main.ApplicationEnv
import attacc.config.ConfigEnv
import attacc.observables.{ObservableList, ObservableMap, ObservableStore, ObservableValue}
import zio.ZIO
import io.circe.generic.auto._

case class ApplicationState(sessions: ObservableMap[UUID, Session], validFlags: ObservableList[String])

object ApplicationState {

  case class Line(line: String, timestamp: LocalDateTime)

  case class Run(
    id: UUID,
    session: UUID,
    interval: UUID,
    targetPkey: String,
    target: Map[String, String],
    startTime: LocalDateTime,
    exitCode: ObservableValue[Option[Int]],
    endTime: ObservableValue[Option[LocalDateTime]],
    timeouted: ObservableValue[Boolean],
    outLines: ObservableList[Line],
    errLines: ObservableList[Line])

  object Run {
    def create(
      id: UUID,
      session: UUID,
      interval: UUID,
      targetPkey: String,
      target: Map[String, String],
      startTime: LocalDateTime
    ): ZIO[ApplicationEnv, Throwable, Run] =
      for {
        exitCode  <- ObservableStore.createValue[Option[Int]](None)
        endTime   <- ObservableStore.createValue[Option[LocalDateTime]](None)
        timeouted <- ObservableStore.createValue(false)
        config    <- ConfigEnv.config[ApplicationEnv]
        outLines  <- ObservableStore.createList[Line](config.statsConfig.lastOutputLines)
        errLines  <- ObservableStore.createList[Line](config.statsConfig.lastOutputLines)
      } yield Run(id, session, interval, targetPkey, target, startTime, exitCode, endTime, timeouted, outLines, errLines)
  }

  case class Interval(
    id: UUID,
    session: UUID,
    startTime: LocalDateTime,
    endTime: ObservableValue[Option[LocalDateTime]],
    runs: ObservableList[UUID])

  object Interval {
    def create(id: UUID, session: UUID, startTime: LocalDateTime): ZIO[ApplicationEnv, Throwable, Interval] =
      for {
        endTime <- ObservableStore.createValue[Option[LocalDateTime]](None)
        runs    <- ObservableStore.createList[UUID]
      } yield Interval(id, session, startTime, endTime, runs)
  }

  case class Session(
    id: UUID,
    hostname: String,
    path: String,
    config: Config,
    startTime: LocalDateTime,
    acceptedFlags: ObservableValue[Long],
    runCounter: ObservableValue[Long],
    intervals: ObservableMap[UUID, Interval],
    activeInterval: ObservableValue[Option[UUID]],
    runMap: ObservableMap[UUID, Int],
    runs: ObservableList[Run])

  object Session {
    def create(
      id: UUID,
      hostname: String,
      path: String,
      config: Config,
      startTime: LocalDateTime
    ): ZIO[ApplicationEnv, Throwable, Session] =
      for {
        intervals      <- ObservableStore.createMap[UUID, Interval]
        activeInterval <- ObservableStore.createValue[Option[UUID]](None)
        runMap         <- ObservableStore.createMap[UUID, Int]
        cfg            <- ConfigEnv.config[ApplicationEnv]
        runs           <- ObservableStore.createList[Run](cfg.statsConfig.lastRuns)
        acceptedFlags  <- ObservableStore.createValue(0L)
        runCounter     <- ObservableStore.createValue(0L)
      } yield Session(id, hostname, path, config, startTime, acceptedFlags, runCounter, intervals, activeInterval, runMap, runs)
  }

  private def create: ZIO[ApplicationEnv, Throwable, ApplicationState] =
    for {
      sessions   <- ObservableStore.createMap[UUID, Session]
      config     <- ConfigEnv.config[ApplicationEnv]
      validFlags <- ObservableStore.createList[String](config.statsConfig.lastFlags)
      state = ApplicationState(sessions, validFlags)
      obsState <- ObservableStore.createValue(state)
      _        <- obsState.name("state")
    } yield state

  def retrieveOrCreateState: ZIO[ApplicationEnv, Throwable, ApplicationState] =
    ObservableStore.retrieveValue[ApplicationState]("state").flatMap {
      case Some(state) => state.get
      case None        => create
    }

}
