package models.lshw

import util.ByteStorageUnit
import play.api.libs.json._

object Memory {
  import Json.toJson
  implicit object MemoryFormat extends Format[Memory] {
    override def reads(json: JsValue) = JsSuccess(Memory(
      ByteStorageUnit((json \ "SIZE").as[Long]),
      (json \ "BANK").as[Int],
      (json \ "DESCRIPTION").as[String],
      (json \ "PRODUCT").as[String],
      (json \ "VENDOR").as[String]
    ))
    override def writes(mem: Memory) = JsObject(Seq(
      "SIZE" -> toJson(mem.size.inBytes),
      "SIZE_S" -> toJson(mem.size.inBytes.toString),
      "SIZE_HUMAN" -> toJson(mem.size.toHuman),
      "BANK" -> toJson(mem.bank),
      "DESCRIPTION" -> toJson(mem.description),
      "PRODUCT" -> toJson(mem.product),
      "VENDOR" -> toJson(mem.vendor)
    ))
  }
}

case class Memory(
  size: ByteStorageUnit, bank: Int, description: String, product: String, vendor: String
) extends LshwAsset {
  import Memory._
  override def toJsValue() = Json.toJson(this)
}

