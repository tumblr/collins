package collins.models.lldp

import play.api.libs.json.Format
import play.api.libs.json.JsObject
import play.api.libs.json.JsSuccess
import play.api.libs.json.JsValue
import play.api.libs.json.Json

object Vlan {
  implicit object VlanFormat extends Format[Vlan] {
    override def reads(json: JsValue) = JsSuccess(Vlan(
      (json \ "ID").as[Int],
      (json \ "NAME").as[String]
    ))
    override def writes(vlan: Vlan) = JsObject(Seq(
      "ID" -> Json.toJson(vlan.id),
      "NAME" -> Json.toJson(vlan.name)
    ))
  }
}

case class Vlan(id: Int, name: String) extends LldpAttribute {
  import Vlan._
  override def toJsValue() = Json.toJson(this)
}
