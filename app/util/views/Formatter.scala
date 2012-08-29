package util
package views

import java.text.SimpleDateFormat
import java.util.Date

// Used with views/asset/list
object Formatter {

  val ISO_8601_FORMAT = "yyyy-MM-dd'T'HH:mm:ss"

  def solrDateFormat(date: Date): String = dateFormat(date) + "Z"

  def dateFormat(date: Date): String = {
    new SimpleDateFormat(ISO_8601_FORMAT).format(date)
  }

  def ellipse(source: String, maxLength: Int = 25, filler: String = "..."): String = {
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
