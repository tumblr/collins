package collins.models.lshw

import play.api.libs.json.Format
import play.api.libs.json.JsObject
import play.api.libs.json.JsSuccess
import play.api.libs.json.JsValue
import play.api.libs.json.Json

object ServerBase {
  implicit object ServerbaseFormat extends Format[ServerBase] {
    override def reads(json: JsValue) = JsSuccess(ServerBase(
      (json \ "DESCRIPTION").as[String],
      (json \ "PRODUCT").as[String],
      (json \ "VENDOR").as[String],
      (json \ "SERIAL").as[String]
    ))
    override def writes(serverbase: ServerBase) = JsObject(Seq(
      "DESCRIPTION" -> Json.toJson(serverbase.description),
      "PRODUCT" -> Json.toJson(serverbase.product),
      "VENDOR" -> Json.toJson(serverbase.vendor),
      "SERIAL" -> Json.toJson(serverbase.serial)
    ))
  }
}

case class ServerBase(
  description: String = "", product: String = "", vendor: String = "", serial: String = ""
) extends LshwAsset {
  import ServerBase._
  override def toJsValue() = Json.toJson(this)

  def isEmpty(): Boolean = description.isEmpty && product.isEmpty && vendor.isEmpty && serial.isEmpty
}
