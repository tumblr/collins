package collins
package action
package executor

import script.CollinScriptExecutor

import play.api.Logger
import scala.collection.SeqProxy


/**
 * An executor which executes Collins actions by calling a CollinScript method.
 *
 * @param command the command to execute via CollinScript call.
 */
case class ScriptActionExecutor(override val command: Seq[String]) extends ActionExecutor {

  override protected val logger = Logger("ScriptActionExecutor")

  override protected def runCommandString(cmd: FormattedValues): String = {
    runCommand(cmd) match {
      case Some(results) => results.toString
      case None => ""
    }
  }

  override protected def runCommandBoolean(cmd: FormattedValues): Boolean = {
    runCommand(cmd) match {
      case Some(results) => results.asInstanceOf[Boolean]
      case None => false
    }
  }

  override protected def runCommand(cmd: AnyRef*): AnyRef = {
    CollinScriptExecutor.runScriptCommand(cmd)
  }

}
