package collins
package script

import java.text.SimpleDateFormat
import java.util.Date


object Formatters extends CollinScript {

  val HUMAN_DATE_FORMAT = "yyyy-MM-dd HH:mm:ss"

  def humanDateFormat(date: String): String = {
    return date
    //new SimpleDateFormat().parse(date).applyPattern(HUMAN_DATE_FORMAT)
  }

  def ellipsis(source: String, maxLength: Int = 25, filler: String = "..."): String = {
    val sourceLength = source.length // 12 characters
    val fillerLength = filler.length // 3 characters
    val maxSize = maxLength - fillerLength // 10 - 3 = 7
    if (sourceLength > maxLength) {
      source.slice(0, maxSize) + filler
    } else {
      source
    }
  }

  def forPercent(d: Double) = "%.2f".format(100*d)

  private[this] val words = """([a-zA-Z]+)""".r
  private[this] val separators = """([^a-zA-Z]+)""".r
  def camelCase(value: String, sep: String = "") = {
    separators.replaceAllIn(words.replaceAllIn(value, m => m.matched.toLowerCase.capitalize), sep)
  }

}
