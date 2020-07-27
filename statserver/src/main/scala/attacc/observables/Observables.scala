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
  def onChange(action: (A, A) => ZIO[Any, Throwable, Unit])(implicit decoder: Decoder[A]): ZIO[ObservableStore, Throwable, ZIO[Any, Throwable, Unit]] =
    ZIO.accessM(_.get.onChange(this, action))
  def modify(f: A => A)(implicit decoder: Decoder[A], encoder: Encoder[A]): ZIO[ObservableStore, Throwable, Unit] = get.flatMap(v => put(f(v)))
}

final case class ObservableMap[A, B] private[observables] (override val id: Long) extends Observable {
  def put(key: A, value: Option[B])(implicit aEncoder: Encoder[A], aDecoder: Decoder[A], bEncoder: Encoder[B], bDecoder: Decoder[B]): ZIO[ObservableStore, Throwable, Boolean] =
    ZIO.accessM(_.get.put(this, key, value))
  def get(key: A)(implicit aEncoder: Encoder[A], aDecoder: Decoder[A], bEncoder: Encoder[B], bDecoder: Decoder[B]): ZIO[ObservableStore, Throwable, Option[B]] =
    ZIO.accessM(_.get.get(this, key))
  def get()(implicit aEncoder: Encoder[A], aDecoder: Decoder[A], bEncoder: Encoder[B], bDecoder: Decoder[B]): fs2.Stream[ZIO[ObservableStore, Throwable, *], (A, B)] =
    fs2.Stream.eval[ZIO[ObservableStore, Throwable, *], fs2.Stream[ZIO[ObservableStore, Throwable, *], (A, B)]](ZIO.access(_.get.get(this))).flatten
  def onChange(action: (A, Option[B]) => ZIO[Any, Throwable, Unit])(implicit aDecoder: Decoder[A], bDecoder: Decoder[B]): ZIO[ObservableStore, Throwable, ZIO[Any, Throwable, Unit]] =
    ZIO.accessM(_.get.onChange(this, action))
}

final case class ObservableList[A] private[observables] (override val id: Long) extends Observable {
  def add(a: A)(implicit encoder: Encoder[A]): ZIO[ObservableStore, Throwable, Int] =
    ZIO.accessM(_.get.add(this, a))
  def get(implicit decoder: Decoder[A]): fs2.Stream[ZIO[ObservableStore, Throwable, *], A] =
    fs2.Stream.eval[ZIO[ObservableStore, Throwable, *], fs2.Stream[ZIO[ObservableStore, Throwable, *], A]](ZIO.access(_.get.get(this))).flatten
  def get(index: Int)(implicit decoder: Decoder[A]): ZIO[ObservableStore, Throwable, Option[A]] =
    ZIO.accessM(_.get.get(this, index))
  def onAdd(action: A => ZIO[Any, Throwable, Unit])(implicit decoder: Decoder[A]): ZIO[ObservableStore, Throwable, ZIO[Any, Throwable, Unit]] =
    ZIO.accessM(_.get.onAdd(this, action))
}
