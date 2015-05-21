package collins.models.lldp

import play.api.libs.json.JsValue

trait LldpAttribute {
  def toJsValue(): JsValue
}
