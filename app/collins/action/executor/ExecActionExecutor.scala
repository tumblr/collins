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
trait ExecActionExecutor extends ActionExecutor {

  override protected def runCommandGeneric(cmd: AnyRef*): Option[AnyRef] = {
    val stringCommand: Seq[String] = cmd.map{ cmdPart =>
      cmdPart.toString
    }
    Some(Command(stringCommand, logger).run())
  }

  override protected def runCommandBoolean(cmd: FormattedValues): Boolean = {
    Command(cmd, logger).run().exitCode == 0
  }

  override protected def runCommandString(cmd: FormattedValues): String = {
    Command(cmd, logger).run().stdout
  }

}
