package attacc

import io.circe.{Decoder, Encoder}
import zio.{Has, ZIO}

package object observables {
  type ObservableStore = Has[ObservableStore.Service]

  object ObservableStore {
    trait Service {
      def name(name: String, obs: Observable): ZIO[Any, Throwable, Unit]
      def retrieveValue[A](name: String): ZIO[Any, Throwable, Option[ObservableValue[A]]]
      def retrieveMap[A, B](name: String): ZIO[Any, Throwable, Option[ObservableMap[A, B]]]
      def retrieveList[A](name: String): ZIO[Any, Throwable, Option[ObservableList[A]]]

      def createValue[A](a: A)(implicit encoder: Encoder[A]): ZIO[Any, Throwable, ObservableValue[A]]
      def retrieveValue[A](id: Long): ZIO[Any, Throwable, Option[ObservableValue[A]]]
      def get[A](obs: ObservableValue[A])(implicit decoder: Decoder[A]): ZIO[Any, Throwable, A]
      def put[A](obs: ObservableValue[A], a: A)(implicit encoder: Encoder[A]): ZIO[Any, Throwable, Unit]
      def onChange[A](obs: ObservableValue[A], action: (A, A) => ZIO[Any, Throwable, Unit])(implicit decoder: Decoder[A]): ZIO[Any, Throwable, ZIO[Any, Throwable, Unit]]

      def createMap[A, B]: ZIO[Any, Throwable, ObservableMap[A, B]]
      def retrieveMap[A, B](id: Long): ZIO[Any, Throwable, Option[ObservableMap[A, B]]]
      def put[A, B](map: ObservableMap[A, B], key: A, value: Option[B])(implicit aEncoder: Encoder[A], aDecoder: Decoder[A], bEncoder: Encoder[B], bDecoder: Decoder[B]): ZIO[Any, Throwable, Boolean]
      def get[A, B](map: ObservableMap[A, B])(implicit aEncoder: Encoder[A], aDecoder: Decoder[A], bEncoder: Encoder[B], bDecoder: Decoder[B]): fs2.Stream[ZIO[Any, Throwable, *], (A, B)]
      def get[A, B](map: ObservableMap[A, B], key: A)(implicit aEncoder: Encoder[A], aDecoder: Decoder[A], bEncoder: Encoder[B], bDecoder: Decoder[B]): ZIO[Any, Throwable, Option[B]]
      def onChange[A, B](map: ObservableMap[A, B], action: (A, Option[B]) => ZIO[Any, Throwable, Unit])(implicit aDecoder: Decoder[A], bDecoder: Decoder[B]): ZIO[Any, Throwable, ZIO[Any, Throwable, Unit]]

      def createList[A](maxLength: Option[Int]): ZIO[Any, Throwable, ObservableList[A]]
      def retrieveList[A](id: Long): ZIO[Any, Throwable, Option[ObservableList[A]]]
      def add[A](list: ObservableList[A], a: A)(implicit encoder: Encoder[A]): ZIO[Any, Throwable, Int]
      def get[A](list: ObservableList[A])(implicit decoder: Decoder[A]): fs2.Stream[ZIO[Any, Throwable, *], A]
      def get[A](list: ObservableList[A], index: Int)(implicit decoder: Decoder[A]): ZIO[Any, Throwable, Option[A]]
      def onAdd[A](list: ObservableList[A], action: A => ZIO[Any, Throwable, Unit])(implicit decoder: Decoder[A]): ZIO[Any, Throwable, ZIO[Any, Throwable, Unit]]
    }

    def retrieveValue[A](name: String): ZIO[ObservableStore, Throwable, Option[ObservableValue[A]]] =
      ZIO.accessM(_.get.retrieveValue(name))
    def retrieveMap[A, B](name: String): ZIO[ObservableStore, Throwable, Option[ObservableMap[A, B]]] =
      ZIO.accessM(_.get.retrieveMap(name))
    def retrieveList[A](name: String): ZIO[ObservableStore, Throwable, Option[ObservableList[A]]] =
      ZIO.accessM(_.get.retrieveList(name))
    def createValue[A](a: A)(implicit encoder: Encoder[A]): ZIO[ObservableStore, Throwable, ObservableValue[A]] =
      ZIO.accessM(_.get.createValue(a))
    def retrieveValue[A](id: Long): ZIO[ObservableStore, Throwable, Option[ObservableValue[A]]] =
      ZIO.accessM(_.get.retrieveValue(id))
    def createMap[A, B]: ZIO[ObservableStore, Throwable, ObservableMap[A, B]] =
      ZIO.accessM(_.get.createMap)
    def retrieveMap[A, B](id: Long): ZIO[ObservableStore, Throwable, Option[ObservableMap[A, B]]] =
      ZIO.accessM(_.get.retrieveMap(id))
    def createList[A]: ZIO[ObservableStore, Throwable, ObservableList[A]] =
      ZIO.accessM(_.get.createList(None))
    def createList[A](maxLength: Int): ZIO[ObservableStore, Throwable, ObservableList[A]] =
      ZIO.accessM(_.get.createList(Some(maxLength)))
    def retrieveList[A](id: Long): ZIO[ObservableStore, Throwable, Option[ObservableList[A]]] =
      ZIO.accessM(_.get.retrieveList(id))
  }

}
