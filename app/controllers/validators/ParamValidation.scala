package controllers
package validators

import play.api.data.Form
import play.api.data.Forms._
import play.api.data.validation.Constraints._

trait ParamValidation {
  protected def validatedText(len: Int) = text(len).verifying { txt =>
    StringUtil.trim(txt) match {
      case None => false
      case Some(v) => v.length >= len
    }
  }
  protected def validatedOptionalText(len: Int) = optional(
    validatedText(len)
  )
}
