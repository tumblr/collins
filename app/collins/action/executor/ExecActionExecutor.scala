package collins
package action
package executor

import shell.{Command, CommandResult}

import play.api.Logger


/**
 * An executor which executes Collins actions via system calls.
 *
 * @param command the command to execute via system call.
 */
case class ExecActionExecutor(override val command: Seq[String]) extends ActionExecutor {

  override protected val logger = Logger("ExecActionExecutor")

  override protected def runCommandString(cmd: FormattedValues): String = {
    Command(cmd, logger).run().stdout
  }

  override protected def runCommandBoolean(cmd: FormattedValues): Boolean = {
    Command(cmd, logger).run().exitCode == 0
  }

  override protected def runCommand(cmd: AnyRef*): AnyRef = {
    val stringCommand: Seq[String] = cmd.map{ cmdPart =>
      cmdPart.toString
    }
    Command(stringCommand, logger).run()
  }

}
