package collins.models.lshw

import play.api.libs.json.Format
import play.api.libs.json.JsObject
import play.api.libs.json.JsSuccess
import play.api.libs.json.JsValue
import play.api.libs.json.Json

import collins.util.Stats
import collins.util.BitStorageUnit

object Nic {
  implicit object NicFormat extends Format[Nic] {
    override def reads(json: JsValue) = Stats.time("Nic.Reads") {
      JsSuccess(Nic(
        BitStorageUnit((json \ "SPEED").as[Long]),
        (json \ "MAC_ADDRESS").as[String],
        (json \ "DESCRIPTION").as[String],
        (json \ "PRODUCT").as[String],
        (json \ "VENDOR").as[String]
      ))
    }
    override def writes(nic: Nic) = Stats.time("Nic.Writes") {
      JsObject(Seq(
        "SPEED" -> Json.toJson(nic.speed.inBits),
        "SPEED_S" -> Json.toJson(nic.speed.inBits.toString),
        "SPEED_HUMAN" -> Json.toJson(nic.speed.toHuman),
        "MAC_ADDRESS" -> Json.toJson(nic.macAddress),
        "DESCRIPTION" -> Json.toJson(nic.description),
        "PRODUCT" -> Json.toJson(nic.product),
        "VENDOR" -> Json.toJson(nic.vendor)
      ))
    }
  }
}

case class Nic(
  speed: BitStorageUnit, macAddress: String, description: String, product: String, vendor: String
) extends LshwAsset {
  import Nic._
  override def toJsValue() = Json.toJson(this)
}
