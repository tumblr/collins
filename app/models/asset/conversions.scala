package models.asset

import models.{Asset, AssetType, Status}
import models.conversions._
import play.api.libs.json._
import java.sql.Timestamp

object conversions {
  implicit object AssetFormat extends Format[AssetView] {
    override def reads(json: JsValue) = Asset(
      (json \ "TAG").as[String],
      Status.findByName((json \ "STATUS").as[String]).map(_.id).get,
      AssetType.findByName((json \ "TYPE").as[String]).map(_.id).get,
      (json \ "CREATED").as[Timestamp],
      (json \ "UPDATED").asOpt[Timestamp],
      (json \ "DELETED").asOpt[Timestamp],
      (json \ "ID").as[Long]
    )
    override def writes(asset: AssetView): JsObject = JsObject(Seq(
      "ID" -> JsNumber(asset.id),
      "TAG" -> JsString(asset.tag),
      "STATUS" -> JsString(asset.getStatusName),
      "TYPE" -> Json.toJson(AssetType.findById(asset.asset_type).map(_.name)),
      "CREATED" -> Json.toJson(asset.created),
      "UPDATED" -> Json.toJson(asset.updated),
      "DELETED" -> Json.toJson(asset.deleted)
    ))
  }
}
