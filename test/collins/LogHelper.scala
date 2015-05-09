package collins

import play.api.{Logger => PlayLogger}
import org.slf4j.LoggerFactory
import ch.qos.logback.classic._

object LogHelper extends LogHelper
trait LogHelper {
  val TRACE = Level.TRACE
  val DEBUG = Level.DEBUG
  val INFO = Level.INFO
  val WARN = Level.WARN
  val ERROR = Level.ERROR
  val OFF = Level.OFF

  val logger = PlayLogger("test")

  def mute(name: Option[String] = None) {
    setLevel(OFF, name)
  }

  def setLevel(level: Level, name: Option[String] = None) {
    val logger = name
                  .map(PlayLogger(_).logger)
                  .getOrElse(PlayLogger.logger)
                  .asInstanceOf[Logger]
    logger.setLevel(level)
  }
}

object LogHelp extends LogHelper
class LogHelp(level: Level, name: Option[String] = None) extends LogHelper {
  setLevel(level, name)
}

trait MutedLogger extends LogHelper {
  mute()
}
