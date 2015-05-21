package collins.models.asset

import java.sql.Timestamp

import play.api.libs.json.Format
import play.api.libs.json.JsNumber
import play.api.libs.json.JsObject
import play.api.libs.json.JsString
import play.api.libs.json.JsSuccess
import play.api.libs.json.JsValue
import play.api.libs.json.Json

import collins.models.Asset
import collins.models.AssetType
import collins.models.State
import collins.models.State.StateFormat
import collins.models.Status
import collins.models.conversions.TimestampFormat

object conversions {
  import collins.models.State.StateFormat
  implicit object AssetFormat extends Format[AssetView] {
    override def reads(json: JsValue) = JsSuccess(Asset(
      (json \ "TAG").as[String],
      Status.findByName((json \ "STATUS").as[String]).map(_.id).get,
      AssetType.findByName((json \ "TYPE").as[String]).map(_.id).get,
      (json \ "CREATED").as[Timestamp],
      (json \ "UPDATED").asOpt[Timestamp],
      (json \ "DELETED").asOpt[Timestamp],
      (json \ "ID").as[Long],
      (json \ "STATE").asOpt[State].map(_.id).getOrElse(0)
    ))
    override def writes(asset: AssetView): JsObject = JsObject(Seq(
      "ID" -> JsNumber(asset.id),
      "TAG" -> JsString(asset.tag),
      "STATE" -> Json.toJson(State.findById(asset.state)),
      "STATUS" -> JsString(asset.getStatusName),
      "TYPE" -> Json.toJson(AssetType.findById(asset.asset_type).map(_.name)),
      "CREATED" -> Json.toJson(asset.created),
      "UPDATED" -> Json.toJson(asset.updated),
      "DELETED" -> Json.toJson(asset.deleted)
    ))
  }
}
