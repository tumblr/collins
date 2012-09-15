package collins
package action
package handler

import action.ActionHandler
import action.executor.{ExecActionExecutor, ScriptActionExecutor}
import models.asset.AssetView


/**
 * An ActionHandler subclass which specifies a set of handlers for Collins
 * Actions corresponding to an AssetView.
 *
 * @param command a sequence of Strings specified by the user in a Collins
 * Action configuration.
 */
trait AssetActionHandler extends ActionHandler {

  /**
   * Runs the command specified in the Collins Action to determine a Boolean
   * value for the specified Asset.
   *
   * @param asset an AssetView corresponding to a Collins Asset.
   * @return a String representing the return value of the Action.
   */
  def checkAssetAction(asset: AssetView): Boolean

  /**
   * Runs the command specified in the Collins Action to determine a String
   * value for the specified Asset.
   *
   * @param asset an AssetView corresponding to a Collins Asset.
   * @return a String representing the return value of the Action.
   */
  def executeAssetAction(asset: AssetView): String

}


/**
 * An AssetActionHandler subclass which executes Collins Actions by way of
 * a system call to the shell.
 *
 * @param command: the Collins Action command to execute
 */
case class AssetExecActionHandler(override val command: Seq[String])
  extends ExecActionExecutor with AssetActionHandler {

  override def checkAssetAction(asset: AssetView): Boolean = {
    runCommandBoolean(templateCommand(asset))
  }

  override def executeAssetAction(asset: AssetView): String = {
    runCommandString(templateCommand(asset))
  }

}


/**
 * An AssetActionHandler subclass which executes Collins Actions by way of
 * a CollinScript method call.
 *
 * @param command: the Collins Action command to execute
 */
case class AssetScriptActionHandler(override val command: Seq[String])
  extends ScriptActionExecutor with AssetActionHandler {

  override def checkAssetAction(asset: AssetView): Boolean = {
    runCommand(templateCommandWithObject(asset) : _*) match {
      case None => false
      case (b: java.lang.Boolean) => b
    }
  }

  override def executeAssetAction(asset: AssetView): String = {
    runCommand(templateCommandWithObject(asset) : _*) match {
      case None => ""
      case s => s.toString
    }
  }

}
