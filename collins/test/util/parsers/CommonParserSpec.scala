package test
package util
package parsers

import org.specs2._
import specification._

trait CommonParserSpec[REP] extends ResourceFinder {

  def getParser(txt: String, options: Map[String,String] = Map.empty): _root_.util.parsers.CommonParser[REP]
  def getParseResults(filename: String, options: Map[String,String] = Map.empty): Either[Throwable,REP] = {
    val data = getResource(filename)
    val parser = getParser(data, options)
    parser.parse()
  }

}
