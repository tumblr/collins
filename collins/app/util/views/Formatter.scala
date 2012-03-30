package util
package views

// Used with views/asset/list
object Formatter {
  def elipse(source: String, maxLength: Int = 25, filler: String = "..."): String = {
    val sourceLength = source.length // 12 characters
    val fillerLength = filler.length // 3 characters
    val maxSize = maxLength - fillerLength // 10 - 3 = 7
    if (sourceLength > maxLength) {
      source.slice(0, maxSize) + filler
    } else {
      source
    }
  }
}
