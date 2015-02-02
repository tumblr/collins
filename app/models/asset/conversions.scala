package models.asset

import models.{Asset, AssetType, State, Status}
import models.conversions._
import play.api.libs.json._
import java.sql.Timestamp

object conversions {
  import models.State.StateFormat
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
