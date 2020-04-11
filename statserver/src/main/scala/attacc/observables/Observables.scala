package attacc.observables

import io.circe.{Decoder, Encoder}
import zio.ZIO

sealed trait Observable {
  val id: Long
  final def name(name: String): ZIO[ObservableStore, Throwable, Unit] =
    ZIO.accessM(_.get.name(name, this))
}
final case class ObservableValue[A] private[observables] (override val id: Long) extends Observable {
  def get(implicit decoder: Decoder[A]): ZIO[ObservableStore, Throwable, A] =
    ZIO.accessM(_.get.get(this))
  def put(a: A)(implicit encoder: Encoder[A]): ZIO[ObservableStore, Throwable, Unit] =
    ZIO.accessM(_.get.put(this, a))
  def onChange(action: (A, A) => ZIO[Any, Throwable, Unit]): ZIO[ObservableStore, Throwable, ZIO[Any, Throwable, Unit]] =
    ZIO.accessM(_.get.onChange(this, action))
}
final case class ObservableMap[A, B] private[observables] (override val id: Long) extends Observable {
  def put(key: A, value: Option[B])(implicit aEncoder: Encoder[A], aDecoder: Decoder[A], bEncoder: Encoder[B], bDecoder: Decoder[B]): ZIO[ObservableStore, Throwable, Boolean] =
    ZIO.accessM(_.get.put(this, key, value))
  def get(key: A)(implicit aEncoder: Encoder[A], aDecoder: Decoder[A], bEncoder: Encoder[B], bDecoder: Decoder[B]): ZIO[ObservableStore, Throwable, Option[B]] =
    ZIO.accessM(_.get.get(this, key))
  def onChange(action: (Option[B], Option[B]) => ZIO[Any, Throwable, Unit]): ZIO[ObservableStore, Throwable, ZIO[Any, Throwable, Unit]] =
    ZIO.accessM(_.get.onChange(this, action))
}

final case class ObservableList[A] private[observables] (override val id: Long) extends Observable {
  def add(a: A)(implicit encoder: Encoder[A]): ZIO[ObservableStore, Throwable, Unit] =
    ZIO.accessM(_.get.add(this, a))
  def get[R <: ObservableStore](implicit decoder: Decoder[A]): fs2.Stream[ZIO[R, Throwable, *], A] =
    fs2.Stream.eval[ZIO[R, Throwable, *], fs2.Stream[ZIO[R, Throwable, *], A]](ZIO.access(_.get.get(this))).flatten
  def onAdd(action: A => ZIO[Any, Throwable, Unit]): ZIO[ObservableStore, Throwable, ZIO[Any, Throwable, Unit]] =
    ZIO.accessM(_.get.onAdd(this, action))
}
