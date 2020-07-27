package attacc

import io.circe.{Decoder, Encoder, Json}
import io.circe.generic.extras.Configuration
import io.circe.syntax._
import io.circe.generic.extras.semiauto.{deriveConfiguredDecoder, deriveConfiguredEncoder}
import io.circe.generic.auto._

sealed trait Query {
  def json: Json = this.asInstanceOf[Query].asJson(Queries.encodeQuery)
}
final case class ListSessions()                       extends Query
final case class ListFlags()                          extends Query
final case class ObserveValue(id: Long)               extends Query
final case class ObserveList(id: Long)                extends Query
final case class ObserveMap(id: Long)                 extends Query
final case class ObserveMapValue(id: Long, key: Json) extends Query

sealed trait QueryResult {
  def json: Json = this.asInstanceOf[QueryResult].asJson
}
final case class ListEntry(value: Json)                   extends QueryResult
final case class MapEntry(key: Json, value: Option[Json]) extends QueryResult
final case class ValueChange(value: Json)                 extends QueryResult

object Queries {

  implicit val config: Configuration = Configuration.default.withSnakeCaseMemberNames.withDiscriminator("query")

  implicit val decodeQuery: Decoder[Query] = deriveConfiguredDecoder[Query]
  implicit val encodeQuery: Encoder[Query] = deriveConfiguredEncoder[Query]

  def fromJson(query: Json): Decoder.Result[Query] = decodeQuery.decodeJson(query)
}
