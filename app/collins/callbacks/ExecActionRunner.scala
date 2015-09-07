package collins.callbacks

import java.beans.PropertyChangeEvent
import java.util.concurrent.atomic.AtomicLong

import play.api.Logger

import collins.shell.Command

object ExecStats {
  val successCount = new AtomicLong(0)
  val failureCount = new AtomicLong(0)
  val totalCount = new AtomicLong(0)

  def reset() {
    successCount.set(0)
    failureCount.set(0)
    totalCount.set(0)
  }
}

case class ExecActionRunner(command: Seq[String]) extends CallbackActionHandler {
  protected val logger = Logger(classOf[ExecActionRunner])

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
      replacements.map(_.toString).mkString(", ")))
    val replacementsWithValues = for {
      reps <- replacements
      d <- value.datum
    } yield reps.runMethod(d)
    logger.debug("Got replacements (with values) for command %s: %s".format(commandString,
      replacementsWithValues.map(_.toString).mkString(", ")))
    val cmdValue = formatCommand(replacementsWithValues)
    logger.debug("Got new command with replacements: %s".format(cmdValue))
    runCommand(cmdValue)
  }

  protected def getMethodReplacements(): Set[MethodReplacement] =
    METHOD_CALL_REGEX.findAllIn(commandString).matchData.map { md =>
      MethodReplacement(md.group(0), md.group(1))
    }.toSet

  def formatCommand(replacements: Set[MethodReplacement]): Seq[String] = {
    command.map { cmd =>
      replacements.foldLeft(cmd) {
        case (string, replacement) =>
          string.replaceAllLiterally(replacement.originalValue, replacement.newValue)
      }
    }
  }

  protected def runCommand(cmd: Seq[String]) {
    val result = Command(cmd, logger).run()
    if (result.isSuccess) {
      ExecStats.successCount.incrementAndGet()
    } else {
      ExecStats.failureCount.incrementAndGet()
    }

    ExecStats.totalCount.incrementAndGet()
  }

}
