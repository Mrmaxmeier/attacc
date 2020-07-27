package attacc.observables

import java.util.NoSuchElementException

import attacc.observables.ObservableStore.Service
import io.circe.{Decoder, Encoder, Json}
import zio.{ZIO, ZLayer}
import zio.stm.TRef
import zio.interop.catz._
import io.circe.syntax._

import scala.collection.immutable.{ArraySeq, Queue}

object InMemoryStore {
  val inMemoryStore: ZLayer[Any, Nothing, ObservableStore] = ZLayer.fromEffect {
    for {
      valuesRef      <- TRef.make(Map[Long, Json]()).commit
      mapsRef        <- TRef.make(Map[Long, Map[Json, Json]]()).commit
      listsRef       <- TRef.make(Map[Long, (Option[Int], Int, Queue[Json])]()).commit
      namesRef       <- TRef.make(Map[String, Long]()).commit
      valueListeners <- TRef.make(Map[Long, List[(Json, Json) => ZIO[Any, Throwable, Unit]]]()).commit
      mapListeners   <- TRef.make(Map[Long, List[(Json, Option[Json]) => ZIO[Any, Throwable, Unit]]]()).commit
      listListeners  <- TRef.make(Map[Long, List[Json => ZIO[Any, Throwable, Unit]]]()).commit
      currentId      <- TRef.make(0L).commit
    } yield new Service {

      override def name(name: String, obs: Observable): ZIO[Any, Throwable, Unit] =
        namesRef.update(_.updated(name, obs.id)).commit

      override def retrieveValue[A](name: String): ZIO[Any, Throwable, Option[ObservableValue[A]]] =
        for {
          names  <- namesRef.get.commit
          values <- valuesRef.get.commit
        } yield names.get(name) match {
          case Some(id) if values.contains(id) =>
            Some(ObservableValue[A](id))
          case _ =>
            None
        }

      override def retrieveMap[A, B](name: String): ZIO[Any, Throwable, Option[ObservableMap[A, B]]] =
        for {
          names <- namesRef.get.commit
          maps  <- mapsRef.get.commit
        } yield names.get(name) match {
          case Some(id) if maps.contains(id) =>
            Some(ObservableMap[A, B](id))
          case _ =>
            None
        }

      override def retrieveList[A](name: String): ZIO[Any, Throwable, Option[ObservableList[A]]] =
        for {
          names <- namesRef.get.commit
          lists <- listsRef.get.commit
        } yield names.get(name) match {
          case Some(id) if lists.contains(id) =>
            Some(ObservableList[A](id))
          case _ =>
            None
        }

      override def createValue[A](a: A)(implicit encoder: Encoder[A]): ZIO[Any, Throwable, ObservableValue[A]] =
        (for {
          id <- currentId.modify(id => (id, id + 1))
          _  <- valuesRef.update(_.updated(id, a.asJson))
        } yield ObservableValue[A](id)).commit

      override def retrieveValue[A](id: Long): ZIO[Any, Throwable, Option[ObservableValue[A]]] =
        valuesRef.get.commit.map(values => if (values contains id) Some(ObservableValue(id)) else None)

      override def get[A](obs: ObservableValue[A])(implicit decoder: Decoder[A]): ZIO[Any, Throwable, A] =
        for {
          values <- valuesRef.get.commit
          value <- values.get(obs.id) match {
            case None        => ZIO.fail(new NoSuchElementException(s"observable value with id ${obs.id} does not exist"))
            case Some(value) => ZIO.succeed(value)
          }
          casted <- value.as[A].map(a => ZIO.succeed(a)).getOrElse(ZIO.fail(new Exception("couldn't cast json value")))
        } yield casted

      override def put[A](obs: ObservableValue[A], a: A)(implicit encoder: Encoder[A]): ZIO[Any, Throwable, Unit] =
        for {
          listeners <- valueListeners.get.commit
          oldValue <- valuesRef
            .modify(values =>
              values.get(obs.id) match {
                case None           => (None, values)
                case Some(oldValue) => (Some(oldValue), values.updated(obs.id, a.asJson))
              }
            )
            .commit
          _ <- oldValue match {
            case None           => ZIO.fail(new NoSuchElementException(s"observable value with id ${obs.id} does not exist"))
            case Some(oldValue) => ZIO.collectAll(listeners.getOrElse(obs.id, List()).map(f => f(oldValue, a.asJson)))
          }
        } yield ()

      override def onChange[A](obs: ObservableValue[A], action: (A, A) => ZIO[Any, Throwable, Unit])(implicit decoder: Decoder[A]): ZIO[Any, Throwable, ZIO[Any, Throwable, Unit]] = {
        val castAction = (a: Json, b: Json) => b.as[A].flatMap(b => a.as[A].map(a => action(a, b))).getOrElse(ZIO.fail(new Exception("could not parse one of the json objects")))
        valueListeners
          .update(m => m.updated(obs.id, castAction :: m.getOrElse(obs.id, List())))
          .commit
          .map(_ => valueListeners.update(m => m.updated(obs.id, m.getOrElse(obs.id, List()).filter(_ != castAction))).commit)
      }

      override def createMap[A, B]: ZIO[Any, Throwable, ObservableMap[A, B]] =
        (for {
          id <- currentId.modify(id => (id, id + 1))
          _  <- mapsRef.update(_.updated(id, Map()))
        } yield ObservableMap[A, B](id)).commit

      override def retrieveMap[A, B](id: Long): ZIO[Any, Throwable, Option[ObservableMap[A, B]]] =
        mapsRef.get.commit.map(maps => if (maps contains id) Some(ObservableMap(id)) else None)

      override def put[A, B](map: ObservableMap[A, B], key: A, value: Option[B])(implicit aEncoder: Encoder[A], aDecoder: Decoder[A], bEncoder: Encoder[B], bDecoder: Decoder[B]): ZIO[Any, Throwable, Boolean] =
        for {
          listeners <- mapListeners.get.commit
          oldValue <- mapsRef
            .modify(maps =>
              maps.get(map.id) match {
                case None => (None, maps)
                case Some(inner) =>
                  (Some(inner), maps.updated(map.id, value match {
                    case None    => inner.removed(key.asJson)
                    case Some(b) => inner.updated(key.asJson, b.asJson)
                  }))
              }
            )
            .commit
          _ <- oldValue match {
            case None           => ZIO.fail(new NoSuchElementException(s"observable map with id ${map.id} does not exist"))
            case Some(oldValue) => ZIO.collectAll(listeners.getOrElse(map.id, List()).map(f => f(key.asJson, value.map(_.asJson))))
          }
        } yield oldValue.contains(key)

      override def get[A, B](map: ObservableMap[A, B])(implicit aEncoder: Encoder[A], aDecoder: Decoder[A], bEncoder: Encoder[B], bDecoder: Decoder[B]): fs2.Stream[ZIO[Any, Throwable, *], (A, B)] =
        for {
          maps <- fs2.Stream.eval(mapsRef.get.commit)
          values <- fs2
            .Stream
            .eval(maps.get(map.id) match {
              case None        => ZIO.fail(new NoSuchElementException(s"observable map with id ${map.id} does not exist"))
              case Some(value) => ZIO.succeed(value.toList.toArray)
            })
          s      <- fs2.Stream(ArraySeq.unsafeWrapArray(values): _*)
          casted <- fs2.Stream.eval(s._1.as[A].flatMap(a => s._2.as[B].map(b => ZIO.succeed((a, b)))).getOrElse(ZIO.fail(new Exception("could not parse one of the json values"))))
        } yield casted

      override def get[A, B](map: ObservableMap[A, B], key: A)(implicit aEncoder: Encoder[A], aDecoder: Decoder[A], bEncoder: Encoder[B], bDecoder: Decoder[B]): ZIO[Any, Throwable, Option[B]] =
        for {
          maps <- mapsRef.get.commit
          value <- maps.get(map.id) match {
            case None        => ZIO.fail(new NoSuchElementException(s"observable map with id ${map.id} does not exist"))
            case Some(value) => ZIO.succeed(value.get(key.asJson))
          }
          casted <- value.map(v => v.as[B].map(b => ZIO.succeed(Some(b))).getOrElse(ZIO.fail(new Exception("could not parse value")))).getOrElse(ZIO.succeed(None))
        } yield casted

      override def onChange[A, B](map: ObservableMap[A, B], action: (A, Option[B]) => ZIO[Any, Throwable, Unit])(implicit aDecoder: Decoder[A], bDecoder: Decoder[B]): ZIO[Any, Throwable, ZIO[Any, Throwable, Unit]] = {
        val castAction = (a: Json, b: Option[Json]) => a.as[A].map(a => action(a, b.flatMap(_.as[B].toOption))).getOrElse(ZIO.fail(new Exception("could not parse one of the json objects")))
        mapListeners
          .update(m => m.updated(map.id, castAction :: m.getOrElse(map.id, List())))
          .commit
          .map(_ => mapListeners.update(m => m.updated(map.id, m.getOrElse(map.id, List()).filter(_ != castAction))).commit)
      }

      override def createList[A](length: Option[Int]): ZIO[Any, Throwable, ObservableList[A]] =
        (for {
          id <- currentId.modify(id => (id, id + 1))
          _  <- listsRef.update(_.updated(id, (length, 0, Queue())))
        } yield ObservableList[A](id)).commit

      override def retrieveList[A](id: Long): ZIO[Any, Throwable, Option[ObservableList[A]]] =
        listsRef.get.commit.map(lists => if (lists contains id) Some(ObservableList(id)) else None)

      override def add[A](list: ObservableList[A], a: A)(implicit encoder: Encoder[A]): ZIO[Any, Throwable, Int] =
        for {
          listeners <- listListeners.get.commit
          i <- listsRef
            .modify(lists =>
              lists.get(list.id) match {
                case None => (-1, lists)
                case Some((length, n, inner)) =>
                  val added = inner.enqueue(a.asJson)
                  (n, lists.updated(list.id, (length, n + 1, length.map(l => if (added.length > l) added.dequeue._2 else added).getOrElse(added))))
              }
            )
            .commit
          _ <- if (i < 0)
            ZIO.fail(new NoSuchElementException(s"observable list with id ${list.id} does not exist"))
          else
            ZIO.collectAll(listeners.getOrElse(list.id, List()).map(f => f(a.asJson)))
        } yield i

      override def get[A](list: ObservableList[A])(implicit decoder: Decoder[A]): fs2.Stream[ZIO[Any, Throwable, *], A] =
        fs2
          .Stream
          .eval[ZIO[Any, Throwable, *], Queue[A]](for {
            lists <- listsRef.get.commit
            value <- lists.get(list.id) match {
              case None               => ZIO.fail(new NoSuchElementException(s"observable map with id ${list.id} does not exist"))
              case Some((_, _, list)) => ZIO.succeed(list.flatMap(_.as[A].toOption.toList))
            }
          } yield value)
          .flatMap(list => fs2.Stream.fromIterator[ZIO[Any, Throwable, *]][A](list.iterator))

      override def get[A](list: ObservableList[A], index: Int)(implicit decoder: Decoder[A]): ZIO[Any, Throwable, Option[A]] =
        for {
          lists <- listsRef.get.commit
          value <- lists.get(list.id) match {
            case None => ZIO.fail(new NoSuchElementException(s"observable map with id ${list.id} does not exist"))
            case Some((_, n, list)) =>
              val i = index + list.length - n
              ZIO.succeed(if (!list.indices.contains(i)) None else Some(list(i)))
          }
          casted <- value.map(_.as[A].map(a => ZIO.succeed(Some(a))).getOrElse(ZIO.fail(new Exception("couldn't parse json value")))).getOrElse(ZIO.succeed(None))
        } yield casted

      override def onAdd[A](list: ObservableList[A], action: A => ZIO[Any, Throwable, Unit])(implicit decoder: Decoder[A]): ZIO[Any, Throwable, ZIO[Any, Throwable, Unit]] = {
        val castAction = (a: Json) => a.as[A].map(a => action(a)).getOrElse(ZIO.fail(new Exception("could not parse json value")))
        listListeners
          .update(m => m.updated(list.id, castAction :: m.getOrElse(list.id, List())))
          .commit
          .map(_ => listListeners.update(m => m.updated(list.id, m.getOrElse(list.id, List()).filter(_ != castAction))).commit)
      }
    }
  }
}
