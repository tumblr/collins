package models.lshw

import play.api.libs.json._

abstract class LshwAsset {
  val description: String
  val product: String
  val vendor: String
  def toJsValue(): JsValue
}
object LshwAsset {
  def apply(desc: String, prod: String, vend: String) = new LshwAsset {
    val description = desc
    val product = prod
    val vendor = vend
    override def toJsValue(): JsValue = throw new Exception("Don't call toJsValue on stubs")
  }
}
