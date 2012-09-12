package controllers
package validators

import play.api.data.Form
import play.api.data.Forms._
import play.api.data.validation.Constraints._

trait ParamValidation {
  protected def validatedText(minLen: Int, maxLen: Int = Int.MaxValue) = text(minLen,maxLen).verifying { txt =>
    StringUtil.trim(txt) match {
      case None => false
      case Some(v) => v.length >= minLen && v.length <= maxLen
    }
  }
  protected def validatedOptionalText(len: Int) = optional(
    validatedText(len)
  )
}
