package util
package views

trait DecoratorParser {
  def apply(key: String, config: DecoratorConfig): DecoratorParser
  def parse(string: String): Seq[String]
}

class IdentityParser extends DecoratorParser {
  def apply(key: String, config: DecoratorConfig) = this
  def parse(string: String): Seq[String] = Seq(string)
}

class DelimiterParser(delimiter: String) extends DecoratorParser {
  def this() = this(" ")
  def apply(key: String, config: DecoratorConfig) = {
    val d = config.delimiter.getOrElse {
      throw DecoratorConfigException(key, "delimiter")
    }
    new DelimiterParser(d)
  }
  def parse(string: String): Seq[String] = {
    string.split(delimiter).toSeq
  }
}

