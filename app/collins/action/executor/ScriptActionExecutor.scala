package collins
package action
package executor

import collins.action.handler.CallbackActionHandler
import collins.action.handler.AssetActionHandler
import script.CollinScriptExecutor

import play.api.Logger
import scala.collection.SeqProxy


/**
 * An executor which executes Collins actions by calling a CollinScript method.
 *
 * @param command the command to execute via CollinScript call.
 */
case class ScriptActionExecutor(override val command: Seq[String])
  extends ActionExecutor with AssetActionHandler with CallbackActionHandler {

  override protected val logger = Logger("ScriptActionExecutor")

  override protected def runCommandString(cmd: FormattedValues): String = {
    runCommand(cmd).asInstanceOf[String]
  }

  override protected def runCommandBoolean(cmd: FormattedValues): Boolean = {
    runCommand(cmd).asInstanceOf[Boolean]
  }

  protected def runCommand(cmd: FormattedValues): AnyRef =
    CollinScriptExecutor.runScriptCommand(cmd)

}
