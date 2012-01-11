package util

import play.api.libs.json._

package object conversions {

  implicit def jsValue2oldJsValue(js: JsValue): JsValueWrapper = new JsValueWrapper(js)

  class JsValueWrapper(js: JsValue) {
    def value: Any = js match {
      case JsNull => null
      case JsUndefined(error) => error
      case JsBoolean(v) => v
      case JsNumber(v) => v
      case JsString(v) => v
      case JsArray(v) => v
      case JsObject(v) => v
    }
  }
}
