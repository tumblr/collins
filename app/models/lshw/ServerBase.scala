package models.lshw

import play.api.libs.json._

object ServerBase {
  import Json._
  implicit object ServerbaseFormat extends Format[ServerBase] {
    override def reads(json: JsValue) = JsSuccess(ServerBase(
      (json \ "DESCRIPTION").as[String],
      (json \ "PRODUCT").as[String],
      (json \ "VENDOR").as[String],
      (json \ "SERIAL").as[String]
    ))
    override def writes(serverbase: ServerBase) = JsObject(Seq(
      "DESCRIPTION" -> toJson(serverbase.description),
      "PRODUCT" -> toJson(serverbase.product),
      "VENDOR" -> toJson(serverbase.vendor),
      "SERIAL" -> toJson(serverbase.serial)
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
