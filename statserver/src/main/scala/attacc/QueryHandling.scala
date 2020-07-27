package attacc

import attacc.Main.ApplicationEnv
import attacc.observables.ObservableStore
import fs2.Stream
import fs2.concurrent.Queue
import io.circe.Json
import zio.interop.catz._
import zio.{RIO, ZIO}
import io.circe.generic.auto._
import io.circe.syntax._
import zio.duration._

object QueryHandling {

  def stream(f: (Json => ZIO[Any, Throwable, Unit]) => ZIO[ApplicationEnv, Throwable, ZIO[Any, Throwable, Unit]]) =
    for {
      q      <- Stream.eval(Queue.unbounded[RIO[Any, *], Json])
      cancel <- Stream.eval(f(json => q.enqueue1(json))) // TODO: remove callback, not possible right now because of api limitations :(
      _      <- Stream.eval(ZIO.sleep(20.minutes).fork.andThen(cancel))
      result <- q.dequeue
    } yield result

  def handle(state: ApplicationState)(query: Query): Stream[ZIO[ApplicationEnv, Throwable, *], Json] = query match {
    case ListSessions() => handle(state)(ObserveMap(state.sessions.id))
    case ListFlags()    => handle(state)(ObserveList(state.validFlags.id))
    case ObserveValue(id) =>
      Stream.eval(ObservableStore.retrieveValue[Json](id)).flatMap {
        case None => Stream.eval(ZIO.fail(new NoSuchElementException(s"No value with id $id found")))
        case Some(value) =>
          for {
            currentValue <- Stream.eval(value.get)
            s            <- Stream(ValueChange(currentValue).asJson) ++ stream(f => value.onChange((_, a) => f(ValueChange(a).asJson)))
          } yield s
      }
    case ObserveList(id) =>
      Stream.eval(ObservableStore.retrieveList[Json](id)).flatMap {
        case None        => Stream.eval(ZIO.fail(new NoSuchElementException(s"No list with id $id found")))
        case Some(value) => value.get.map(v => ListEntry(v).asJson) ++ stream(f => value.onAdd(a => f(ListEntry(a).asJson)))
      }
    case ObserveMap(id) =>
      Stream.eval(ObservableStore.retrieveMap[Json, Json](id)).flatMap {
        case None        => Stream.eval(ZIO.fail(new NoSuchElementException(s"No map with id $id found")))
        case Some(value) => value.get.map(p => MapEntry(p._1, Some(p._2)).asJson) ++ stream(f => value.onChange((a, b) => f(MapEntry(a, b).asJson)))
      }
    case ObserveMapValue(id, key) =>
      Stream.eval(ObservableStore.retrieveMap[Json, Json](id)).flatMap {
        case None => Stream.eval(ZIO.fail(new NoSuchElementException(s"No map with id $id found")))
        case Some(value) =>
          for {
            currentValue <- Stream.eval(value.get(key))
            s            <- Stream(MapEntry(key, currentValue).asJson) ++ stream(f => value.onChange((k, v) => ZIO.when(k == key)(f(MapEntry(key, v).asJson))))
          } yield s
      }
  }

}
