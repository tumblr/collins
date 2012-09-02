package collins
package callbacks

import play.api.Logger
import java.beans.PropertyChangeEvent

trait CallbackActionRunner[T] extends CallbackActionHandler {
  protected val logger = Logger("CallbackActionHandler")

  val command: Seq[String]
  val METHOD_CALL_REGEX = """\<(.*?)\>""".r

  def commandString = command.mkString(" ")

  override def apply(pce: PropertyChangeEvent) {
    val value = getValue(pce)
    if (value == null) {
      logger.warn("Got no value back to use with command %s".format(commandString))
      return
    }
    val replacements = getMethodReplacements()
    logger.debug("Got replacements for command %s: %s".format(commandString,
      replacements.map(_.toString).mkString(", ")
    ))
    val replacementsWithValues = replacements.map(_.runMethod(value))
    logger.debug("Got replacements (with values) for command %s: %s".format(commandString,
      replacementsWithValues.map(_.toString).mkString(", ")
    ))
    val cmdValue = formatCommand(value, replacementsWithValues)
    logger.debug("Got new command with replacements: %s".format(cmdValue))
    runCommand(cmdValue)
  }

  protected def runCommand(cmd: T): Unit
  protected def formatCommand(v: AnyRef, replacements: Set[MethodReplacement]): T

  protected def getMethodReplacements(): Set[MethodReplacement] =
    METHOD_CALL_REGEX.findAllIn(commandString).matchData.map { md =>
      MethodReplacement(md.group(0), md.group(1))
    }.toSet

  protected def getValue(pce: PropertyChangeEvent): AnyRef =
    Option(pce.getNewValue).getOrElse(pce.getOldValue)
}
