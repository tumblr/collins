package collins.models.lldp

import play.api.libs.json.Format
import play.api.libs.json.JsObject
import play.api.libs.json.JsSuccess
import play.api.libs.json.JsValue
import play.api.libs.json.Json

object ChassisId {
  implicit object ChassisIdFormat extends Format[ChassisId] {
    override def reads(json: JsValue) = JsSuccess(ChassisId(
      (json \ "TYPE").as[String],
      (json \ "VALUE").as[String]
    ))
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
