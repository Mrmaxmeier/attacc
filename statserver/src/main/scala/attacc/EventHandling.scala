package attacc

import attacc.Main.ApplicationEnv
import zio.ZIO
import ApplicationState._
import io.circe.generic.auto._
import zio.logging.log

object EventHandling {

  def handle(state: ApplicationState)(event: Event): ZIO[ApplicationEnv, Throwable, Unit] = {
    val getSession = state
      .sessions
      .get(event.sessionId)
      .flatMap {
        case Some(session) => ZIO.succeed(session)
        case None          => ZIO.fail(new IllegalArgumentException(s"Session ${event.sessionId} does not exist!"))
      }

    def getActiveInterval(session: Session) =
      session
        .activeInterval
        .get
        .flatMap {
          case Some(uuid) => ZIO.succeed(uuid)
          case None       => ZIO.fail(new Exception("No active Interval to end!"))
        }
        .flatMap(uuid =>
          session.intervals.get(uuid).flatMap {
            case Some(active) => ZIO.succeed(active)
            case None         => ZIO.fail(new Exception("Active interval not found in interval map!"))
          }
        )

    def getRun(session: Session, run: attacc.Run) = session.runMap.get(run.id).flatMap {
      case Some(i) =>
        session.runs.get(i).flatMap {
          case Some(run) => ZIO.succeed(run)
          case None      => ZIO.fail(new Exception(s"Run with id ${run.id} already evicted!"))
        }
      case None => ZIO.fail(new Exception(s"Run with id ${run.id} not found!"))
    }

    event.payload match {
      case SessionAnnouncement(hostname, path, config) =>
        for {
          _       <- log.info(s"New session: ${event.sessionId}: ${event.timestamp}")
          session <- Session.create(event.sessionId, hostname, path, config, event.timestamp)
          _       <- state.sessions.put(event.sessionId, Some(session))
        } yield ()
      case IntervalStart() =>
        for {
          session  <- getSession
          uuid     <- Util.randomUUID
          _        <- log.info(s"New interval: $uuid for session ${event.sessionId}: ${event.timestamp}")
          interval <- Interval.create(uuid, session.id, event.timestamp)
          _        <- session.intervals.put(uuid, Some(interval))
          _        <- session.activeInterval.put(Some(uuid))
        } yield ()
      case IntervalEnd() =>
        for {
          session        <- getSession
          activeInterval <- getActiveInterval(session)
          _              <- log.info(s"Interval ended: ${activeInterval.id} for session ${event.sessionId}: ${event.timestamp}")
          _              <- activeInterval.endTime.put(Some(event.timestamp))
          _              <- session.activeInterval.put(None)
        } yield ()
      case RunStart(run) =>
        for {
          session        <- getSession
          activeInterval <- getActiveInterval(session)
          observableRun  <- Run.create(run.id, event.sessionId, activeInterval.id, run.key, run.target, event.timestamp)
          index          <- session.runs.add(observableRun)
          _              <- session.runMap.put(run.id, Some(index))
          _              <- activeInterval.runs.add(run.id)
        } yield ()
      case RunTimeout(run) =>
        for {
          session       <- getSession
          observableRun <- getRun(session, run)
          _             <- observableRun.timeouted.put(true)
          _             <- observableRun.endTime.put(Some(event.timestamp))
        } yield ()
      case RunExit(run, exitCode) =>
        for {
          session       <- getSession
          observableRun <- getRun(session, run)
          _             <- observableRun.exitCode.put(Some(exitCode))
          _             <- observableRun.endTime.put(Some(event.timestamp))
          _             <- ZIO.when(exitCode == 0)(session.runCounter.modify(_ + 1))
        } yield ()
      case StdoutLine(run, line) =>
        for {
          session       <- getSession
          observableRun <- getRun(session, run)
          _             <- observableRun.outLines.add(Line(line, event.timestamp))
        } yield ()
      case StderrLine(run, line) =>
        for {
          session       <- getSession
          observableRun <- getRun(session, run)
          _             <- observableRun.errLines.add(Line(line, event.timestamp))
        } yield ()
      case FlagMatch(run, flag, isUnique) => ZIO.succeed(())
      case FlagPending(run, flag)         => ZIO.succeed(())
      case FlagVerdict(run, flag, verdict) =>
        ZIO.when(verdict.contains("accepted"))(for {
          session <- getSession
          _       <- state.validFlags.add(flag)
          _       <- session.acceptedFlags.modify(_ + 1)
        } yield ())
    }
  }
}
