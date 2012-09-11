package collins
package action
package executor

import action.handler.CallbackActionHandler
import action.handler.AssetActionHandler

import play.api.Logger
import scala.collection.mutable.StringBuilder
import scala.sys.process._


/**
 * An executor which executes Collins actions via system calls.
 *
 * @param command the command to execute via system call.
 */
case class ExecActionExecutor(override val command: Seq[String])
  extends ActionExecutor with AssetActionHandler with CallbackActionHandler {

  override protected val logger = Logger("ExecActionExecutor")

  override protected def runCommandString(cmd: FormattedValues): String = {
    runCommand(cmd)._2
  }

  override protected def runCommandBoolean(cmd: FormattedValues): Boolean = {
    runCommand(cmd)._1 == 0
  }

  protected def runCommand(cmd: FormattedValues):
    Tuple3[Int, String, String] = {
    val process = if (cmd.size == 1) {
      // If we got a string which was converted to a list, use the normal Process parsing
      Process(cmd.head)
    } else {
      Process(cmd)
    }
    val stdout = new StringBuilder()
    val stderr = new StringBuilder()
    val exitStatus = try {
      process ! ProcessLogger(
        s => stdout.append(s + "\n"),
        e => stderr.append(e + "\n")
      )
    } catch {
      case e =>
        stderr.append(e.getMessage)
        -1
    }
    logger.info("Ran command %s".format(cmd))
    logger.info("Exit status was %d".format(exitStatus))
    logger.info("Stdout: %s".format(stdout.toString))
    logger.info("Stderr: %s".format(stderr.toString))
    (exitStatus, stdout.mkString, stderr.mkString)
  }

}
