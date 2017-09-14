package collins.models.lshw

import play.api.libs.json.Format
import play.api.libs.json.JsObject
import play.api.libs.json.JsSuccess
import play.api.libs.json.JsValue
import play.api.libs.json.Json

object ServerBase {
  implicit object ServerbaseFormat extends Format[ServerBase] {
    override def reads(json: JsValue) = JsSuccess(ServerBase(
      (json \ "MOTHERBOARD").asOpt[String],
      (json \ "FIRMWARE").asOpt[String],
      (json \ "FIRMWAREDATE").asOpt[String],
      (json \ "DESCRIPTION").as[String],
      (json \ "PRODUCT").as[String],
      (json \ "VENDOR").as[String],
      (json \ "SERIAL").asOpt[String]))
    override def writes(serverbase: ServerBase) = JsObject(Seq(
      "MOTHERBOARD" -> Json.toJson(serverbase.motherboard),
      "FIRMWARE" -> Json.toJson(serverbase.firmware),
      "FIRMWAREDATE" -> Json.toJson(serverbase.firmwaredate),
      "DESCRIPTION" -> Json.toJson(serverbase.description),
      "PRODUCT" -> Json.toJson(serverbase.product),
      "VENDOR" -> Json.toJson(serverbase.vendor),
      "SERIAL" -> Json.toJson(serverbase.serial)))
  }
}

case class ServerBase(
    motherboard: Option[String] = None, firmware: Option[String] = None, firmwaredate: Option[String] = None, description: String = "", product: String = "", vendor: String = "", serial: Option[String] = None) extends LshwAsset {
  import ServerBase._
  override def toJsValue() = Json.toJson(this)

  def isEmpty(): Boolean = motherboard.isEmpty && firmware.isEmpty && firmwaredate.isEmpty && description.isEmpty && product.isEmpty && vendor.isEmpty && serial.isEmpty
}
