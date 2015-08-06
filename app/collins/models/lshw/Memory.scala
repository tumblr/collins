package collins.models.lshw

import play.api.libs.json.Format
import play.api.libs.json.JsObject
import play.api.libs.json.JsSuccess
import play.api.libs.json.JsValue
import play.api.libs.json.Json

import collins.util.Stats
import collins.util.ByteStorageUnit

object Memory {
  implicit object MemoryFormat extends Format[Memory] {
    override def reads(json: JsValue) = Stats.time("Memory.Reads") {
      JsSuccess(Memory(
        ByteStorageUnit((json \ "SIZE").as[Long]),
        (json \ "BANK").as[Int],
        (json \ "DESCRIPTION").as[String],
        (json \ "PRODUCT").as[String],
        (json \ "VENDOR").as[String]
      ))
    }
    override def writes(mem: Memory) = Stats.time("Memory.Writes") {
      JsObject(Seq(
        "SIZE" -> Json.toJson(mem.size.inBytes),
        "SIZE_S" -> Json.toJson(mem.size.inBytes.toString),
        "SIZE_HUMAN" -> Json.toJson(mem.size.toHuman),
        "BANK" -> Json.toJson(mem.bank),
        "DESCRIPTION" -> Json.toJson(mem.description),
        "PRODUCT" -> Json.toJson(mem.product),
        "VENDOR" -> Json.toJson(mem.vendor)
      ))
    }
  }
}

case class Memory(
  size: ByteStorageUnit, bank: Int, description: String, product: String, vendor: String
) extends LshwAsset {
  import Memory._
  override def toJsValue() = Json.toJson(this)
}

