package models.lldp

import play.api.libs.json._

object ChassisId {
  implicit object ChassisIdFormat extends Format[ChassisId] {
    override def reads(json: JsValue) = ChassisId(
      (json \ "TYPE").as[String],
      (json \ "VALUE").as[String]
    )
    override def writes(cid: ChassisId) = JsObject(Seq(
      "TYPE" -> Json.toJson(cid.idType),
      "VALUE" -> Json.toJson(cid.value)
    ))
  }
}

case class ChassisId(idType: String, value: String) extends LldpAttribute {
  import ChassisId._
  override def toJsValue() = Json.toJson(this)
}
