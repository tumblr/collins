package models.lldp

import play.api.libs.json._

trait LldpAttribute {
  def toJsValue(): JsValue
}
