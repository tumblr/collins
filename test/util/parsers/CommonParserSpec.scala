package test
package util
package parsers

import play.api.Configuration
import _root_.util.config.LldpConfig
import _root_.util.config.LshwConfig
import org.specs2._
import specification._

trait CommonParserSpec[REP] extends ResourceFinder {

  def getParser(txt: String): _root_.util.parsers.CommonParser[REP]
  def getParseResults(filename: String, options: Map[String,String] = Map.empty): Either[Throwable,REP] = {
    val data = getResource(filename)
    if (options.nonEmpty) {
      val cfg = Configuration.from(options)
      _root_.util.config.AppConfig.globalConfig = Some(cfg);
    } else {
      _root_.util.config.AppConfig.globalConfig = None;
    }
    LshwConfig.initialize
    LldpConfig.initialize
    val parser = getParser(data)
    parser.parse()
  }

}
