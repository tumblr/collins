package collins.util.parsers

import play.api.Configuration
import collins.ResourceFinder
import collins.util.config.LldpConfig
import collins.util.config.LshwConfig
import org.specs2._
import specification._

trait CommonParserSpec[REP] extends ResourceFinder {

  def getParser(txt: String): collins.util.parsers.CommonParser[REP]
  def getParseResults(filename: String): Either[Throwable,REP] = {
    val data = getResource(filename)
    val parser = getParser(data)
    parser.parse()
  }

}
