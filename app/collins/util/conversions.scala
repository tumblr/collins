package collins.util

import play.api.libs.json.JsArray
import play.api.libs.json.JsBoolean
import play.api.libs.json.JsNull
import play.api.libs.json.JsNumber
import play.api.libs.json.JsObject
import play.api.libs.json.JsString
import play.api.libs.json.JsUndefined
import play.api.libs.json.JsValue

package object conversions {

  implicit def jsValue2oldJsValue(js: JsValue): JsValueWrapper = new JsValueWrapper(js)

  class JsValueWrapper(js: JsValue) {
    def value: Any = js match {
      case JsNull => null
      case und: JsUndefined => und.error
      case JsBoolean(v) => v
      case JsNumber(v) => v
      case JsString(v) => v
      case JsArray(v) => v
      case JsObject(v) => v
    }
  }
}
