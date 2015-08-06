package collins.models.lldp

import play.api.libs.json.Format
import play.api.libs.json.JsObject
import play.api.libs.json.JsSuccess
import play.api.libs.json.JsValue
import play.api.libs.json.Json

import collins.util.Stats

import collins.models.lldp.PortId.PortIdFormat

object Port {
  import PortId._
  implicit object PortFormat extends Format[Port] {
    override def reads(json: JsValue) = Stats.time("Port.Reads") {
      JsSuccess(Port(
        (json \ "ID").as[PortId],
        (json \ "DESCRIPTION").as[String]
      ))
    }
    override def writes(port: Port) = Stats.time("Port.Writes") {
      JsObject(Seq(
        "ID" -> Json.toJson(port.id),
        "DESCRIPTION" -> Json.toJson(port.description)
      ))
    }
  }
}

case class Port(id: PortId, description: String) extends LldpAttribute {
  import Port._
  def isLocal: Boolean = id.idType == "local"
  def toInt: Int = id.value.toInt
  override def toJsValue() = Json.toJson(this)
}
