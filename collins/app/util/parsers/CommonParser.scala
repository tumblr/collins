package util
package parsers

import play.api._

case class AttributeNotFoundException(msg: String) extends Exception(msg)

abstract class CommonParser[T](txt: String, config: Map[String, String] = Map.empty) {
  require(txt != null && txt.length > 0, "Can not parse empty files")
  protected[this] val logger = Logger.logger

  def parse(): Either[Throwable,T]
}
