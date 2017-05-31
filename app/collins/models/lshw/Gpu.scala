package collins.models.lshw

import play.api.libs.json.Format
import play.api.libs.json.JsObject
import play.api.libs.json.JsSuccess
import play.api.libs.json.JsValue
import play.api.libs.json.Json

object Gpu {

  implicit object GpuFormat extends Format[Gpu] {
    override def reads(json: JsValue) = JsSuccess(Gpu(
      (json \ "DESCRIPTION").as[String],
      (json \ "PRODUCT").as[String],
      (json \ "VENDOR").as[String]))
    override def writes(gpu: Gpu) = JsObject(Seq(
      "DESCRIPTION" -> Json.toJson(gpu.description),
      "PRODUCT" -> Json.toJson(gpu.product),
      "VENDOR" -> Json.toJson(gpu.vendor)))
  }
}

case class Gpu(
    description: String, product: String, vendor: String) extends LshwAsset {
  import Gpu._
  override def toJsValue() = Json.toJson(this)
}
