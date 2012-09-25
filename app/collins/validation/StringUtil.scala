package collins.validation

import org.apache.commons.lang3.StringUtils

object StringUtil {
  // all leading/trailing control characters
  def trim(s: String): Option[String] = Option(StringUtils.trimToNull(s))
  // just leading/trailing whitespace
  def strip(s: String): Option[String] = Option(StringUtils.stripToNull(s))
}
