package collins
package action
package executor

import action.handler.CallbackActionHandler
import action.handler.AssetActionHandler
import shell.{Command, CommandResult}

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
    runCommand(cmd).stdout
  }

  override protected def runCommandBoolean(cmd: FormattedValues): Boolean = {
    runCommand(cmd).exitCode == 0
  }

  protected def runCommand(cmd: FormattedValues): CommandResult = {
    Command(cmd, logger).run()
  }

}
