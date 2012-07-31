package util
package views

import play.api.Configuration

trait DecoratorParser {
  def apply(key: String, config: Configuration): DecoratorParser
  def parse(string: String): Seq[String]
}

class IdentityParser extends DecoratorParser {
  def apply(key: String, config: Configuration) = this
  def parse(string: String): Seq[String] = Seq(string)
}

class DelimiterParser(delimiter: String) extends DecoratorParser {
  def this() = this(" ")
  def apply(key: String, config: Configuration) = {
    val d = config.getString("delimiter").getOrElse {
      throw DecoratorConfigurationException(key, "delimiter")
    }
    new DelimiterParser(d)
  }
  def parse(string: String): Seq[String] = {
    string.split(delimiter).toSeq
  }
}

