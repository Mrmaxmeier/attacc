package attacc.observables

import java.util.NoSuchElementException

import attacc.observables.ObservableStore.Service
import io.circe.{Decoder, Encoder}
import zio.{ZIO, ZLayer}
import zio.stm.TRef
import zio.interop.catz._

object InMemoryStore {
  val inMemoryStore: ZLayer[Any, Nothing, ObservableStore] = ZLayer.fromEffect {
    for {
      valuesRef      <- TRef.make(Map[Long, Any]()).commit
      mapsRef        <- TRef.make(Map[Long, Map[Any, Any]]()).commit
      listsRef       <- TRef.make(Map[Long, List[Any]]()).commit
      namesRef       <- TRef.make(Map[String, Long]()).commit
      valueListeners <- TRef.make(Map[Long, List[(Any, Any) => ZIO[Any, Throwable, Unit]]]()).commit
      mapListeners   <- TRef.make(Map[Long, List[(Option[Any], Option[Any]) => ZIO[Any, Throwable, Unit]]]()).commit
      listListeners  <- TRef.make(Map[Long, List[Any => ZIO[Any, Throwable, Unit]]]()).commit
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
          _  <- valuesRef.update(_.updated(id, a))
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
        } yield value.asInstanceOf[A]
      override def put[A](obs: ObservableValue[A], a: A)(implicit encoder: Encoder[A]): ZIO[Any, Throwable, Unit] =
        for {
          listeners <- valueListeners.get.commit
          oldValue <- valuesRef
            .modify(values =>
              values.get(obs.id) match {
                case None           => (None, values)
                case Some(oldValue) => (Some(oldValue), values.updated(obs.id, a))
              }
            )
            .commit
          _ <- oldValue match {
            case None           => ZIO.fail(new NoSuchElementException(s"observable value with id ${obs.id} does not exist"))
            case Some(oldValue) => ZIO.collectAll(listeners.getOrElse(obs.id, List()).map(f => f(oldValue, a)))
          }
        } yield ()
      override def onChange[A](obs: ObservableValue[A], action: (A, A) => ZIO[Any, Throwable, Unit]): ZIO[Any, Throwable, ZIO[Any, Throwable, Unit]] = {
        val castAction = (a: Any, b: Any) => action(a.asInstanceOf[A], b.asInstanceOf[A])
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
                    case None    => inner.removed(key)
                    case Some(b) => inner.updated(key, b)
                  }))
              }
            )
            .commit
          _ <- oldValue match {
            case None           => ZIO.fail(new NoSuchElementException(s"observable map with id ${map.id} does not exist"))
            case Some(oldValue) => ZIO.collectAll(listeners.getOrElse(map.id, List()).map(f => f(oldValue.get(key), value)))
          }
        } yield oldValue.contains(key)
      override def get[A, B](map: ObservableMap[A, B], key: A)(implicit aEncoder: Encoder[A], aDecoder: Decoder[A], bEncoder: Encoder[B], bDecoder: Decoder[B]): ZIO[Any, Throwable, Option[B]] =
        for {
          maps <- mapsRef.get.commit
          value <- maps.get(map.id) match {
            case None        => ZIO.fail(new NoSuchElementException(s"observable map with id ${map.id} does not exist"))
            case Some(value) => ZIO.succeed(value.get(key))
          }
        } yield value.map(_.asInstanceOf[B])
      override def onChange[A, B](map: ObservableMap[A, B], action: (Option[B], Option[B]) => ZIO[Any, Throwable, Unit]): ZIO[Any, Throwable, ZIO[Any, Throwable, Unit]] = {
        val castAction = (a: Option[Any], b: Option[Any]) => action(a.map(_.asInstanceOf[B]), b.map(_.asInstanceOf[B]))
        mapListeners
          .update(m => m.updated(map.id, castAction :: m.getOrElse(map.id, List())))
          .commit
          .map(_ => mapListeners.update(m => m.updated(map.id, m.getOrElse(map.id, List()).filter(_ != castAction))).commit)
      }

      override def createList[A]: ZIO[Any, Throwable, ObservableList[A]] =
        (for {
          id <- currentId.modify(id => (id, id + 1))
          _  <- listsRef.update(_.updated(id, List()))
        } yield ObservableList[A](id)).commit
      override def retrieveList[A](id: Long): ZIO[Any, Throwable, Option[ObservableList[A]]] =
        listsRef.get.commit.map(lists => if (lists contains id) Some(ObservableList(id)) else None)
      override def add[A](list: ObservableList[A], a: A)(implicit encoder: Encoder[A]): ZIO[Any, Throwable, Unit] =
        for {
          listeners <- listListeners.get.commit
          successful <- listsRef
            .modify(lists =>
              lists.get(list.id) match {
                case None => (false, lists)
                case Some(inner) =>
                  (true, lists.updated(list.id, a :: inner))
              }
            )
            .commit
          _ <- if (!successful)
            ZIO.fail(new NoSuchElementException(s"observable list with id ${list.id} does not exist"))
          else
            ZIO.collectAll(listeners.getOrElse(list.id, List()).map(f => f(a)))
        } yield ()
      override def get[A, R](list: ObservableList[A])(implicit decoder: Decoder[A]): fs2.Stream[ZIO[R, Throwable, *], A] =
        fs2
          .Stream
          .eval[ZIO[R, Throwable, *], List[A]](for {
            lists <- listsRef.get.commit
            value <- lists.get(list.id) match {
              case None       => ZIO.fail(new NoSuchElementException(s"observable map with id ${list.id} does not exist"))
              case Some(list) => ZIO.succeed(list.map(_.asInstanceOf[A]))
            }
          } yield value)
          .flatMap(list => fs2.Stream.fromIterator[ZIO[R, Throwable, *]][A](list.iterator))
      override def onAdd[A](list: ObservableList[A], action: A => ZIO[Any, Throwable, Unit]): ZIO[Any, Throwable, ZIO[Any, Throwable, Unit]] = {
        val castAction = (a: Any) => action(a.asInstanceOf[A])
        listListeners
          .update(m => m.updated(list.id, castAction :: m.getOrElse(list.id, List())))
          .commit
          .map(_ => listListeners.update(m => m.updated(list.id, m.getOrElse(list.id, List()).filter(_ != castAction))).commit)
      }
    }
  }
}
