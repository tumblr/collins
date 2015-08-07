package collins.models.lldp

import play.api.libs.json.Format
import play.api.libs.json.JsObject
import play.api.libs.json.JsSuccess
import play.api.libs.json.JsValue
import play.api.libs.json.Json

import collins.models.lldp.ChassisId.ChassisIdFormat

object Chassis {
  import ChassisId._
  implicit object ChassisFormat extends Format[Chassis] {
    override def reads(json: JsValue) = JsSuccess(Chassis(
      (json \ "NAME").as[String],
      (json \ "ID").as[ChassisId],
      (json \ "DESCRIPTION").as[String]
    ))
    override def writes(chassis: Chassis) = JsObject(Seq(
      "NAME" -> Json.toJson(chassis.name),
      "ID" -> Json.toJson(chassis.id),
      "DESCRIPTION" -> Json.toJson(chassis.description)
    ))
  }
}

case class Chassis(name: String, id: ChassisId, description: String) extends LldpAttribute {
  def macAddress: String = id.value
  override def toJsValue() = Json.toJson(this)
}

