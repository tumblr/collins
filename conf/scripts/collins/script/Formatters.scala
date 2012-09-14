package collins
package script

import java.text.SimpleDateFormat
import java.util.Date


/**
 * A collection of formatters to aid in the formatting of Collins objects to
 * textual forms.
 */
object Formatters extends CollinScript {

  /**
   * A formatter which parses and formats a human-readable date format.
   */
  val humanDateFormatter = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss")

  /**
   * A formatter which formats and parses a JDBC-formatted date format.
   */
  val JDBCDateFormatter = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSSS")

  private[this] val words = """([a-zA-Z]+)""".r
  private[this] val separators = """([^a-zA-Z]+)""".r

  /**
   * Camel-cases all words in a String, allowing for the optional specification
   * of a new word separator, defaulting to None.
   *
   * @param value a String whose words will be camel-cased.
   * @param sep an optional word separator.
   * @return a String where all words have been camel-cased/separated.
   */
  def camelCase(value: String, sep: String = ""): String = {
    separators.replaceAllIn(words.replaceAllIn(value,
        m => m.matched.toLowerCase.capitalize), sep)
  }

  /**
   * Limits the output of a source String by a finite length, substituting a
   * filler String for the remaining portion.
   *
   * @param source a String source to limit length of.
   * @param maxLength the maximal length of the String, defaults to 25.
   * @param filler the filler String to substitute, defaults to "..."
   * @return a length-limited String.
   */
  def ellipsis(source: String, maxLength: Int = 25,
      filler: String = "..."): String = {
    val sourceLength = source.length // 12 characters
    val fillerLength = filler.length // 3 characters
    val maxSize = maxLength - fillerLength // 12 - 3 = 9
    if (sourceLength > maxLength) {
      source.slice(0, maxSize) + filler
    } else {
      source
    }
  }

  /**
   * Renders a Double into a human-readable String.
   *
   * @param d a Double to format into human-readable form.
   * @return a 2-decimal precision String representing the double.
   */
  def forPercent(d: Double): String = "%.2f".format(100*d)
  
  /**
   * Formats a JDBC-formatted timestamp String into human-readable form.
   *
   * @param date a String containing a JDBC-formatted timestamp.
   * @return a human-readable String representation of the timestamp.
   */
  def humanDateFormat(date: String): String = {
    humanDateFormatter.format(JDBCDateFormatter.parse(date))
  }

}
