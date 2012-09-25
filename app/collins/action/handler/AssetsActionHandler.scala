package collins
package action
package handler

import action.ActionHandler
import action.executor.{ExecActionExecutor, ScriptActionExecutor}
import models.asset.AssetView
import models.Page


/**
 * An ActionHandler subclass which specifies a set of handlers for Collins
 * Actions corresponding to a Page of AssetViews.
 *
 * @param command a sequence of Strings specified by the user in a Collins
 * Action configuration.
 */
trait AssetsActionHandler extends ActionHandler {

  /**
   * Runs the command specified in the Collins Action to determine a Boolean
   * value for the specified Page of AssetViews.
   *
   * @param asset a Page of AssetViews.
   * @return a String representing the return value of the Action.
   */
  def checkAssetsAction(assets: Page[AssetView]): Boolean

  /**
   * Runs the command specified in the Collins Action to determine a String
   * value for the specified page of AssetViews.
   *
   * @param asset a Page of AssetViews.
   * @return a String representing the return value of the Action.
   */
  def executeAssetsAction(assets: Page[AssetView]): String

}


/**
 * An AssetsActionHandler subclass which executes Collins Actions by way of
 * a system call to the shell.
 *
 * @param command the Collins Action command to execute
 */
case class AssetsExecActionHandler(override val command: Seq[String])
  extends ExecActionExecutor with AssetsActionHandler {

  override def checkAssetsAction(assets: Page[AssetView]): Boolean = {
    runCommandBoolean(templateFormattedValues(assets))
  }

  override def executeAssetsAction(assets: Page[AssetView]): String = {
    runCommandString(templateFormattedValues(assets))
  }

}


/**
 * An AssetsActionHandler subclass which executes Collins Actions by way of
 * a CollinScript method call.
 *
 * @param command the Collins Action command to execute
 */
case class AssetsScriptActionHandler(override val command: Seq[String])
  extends ScriptActionExecutor with AssetsActionHandler {

  override def checkAssetsAction(assets: Page[AssetView]): Boolean = {
    runCommandGeneric(templateCommandWithObject(assets) : _*) match {
      case None => false
      case Some(b: java.lang.Boolean) => b
      case _ => false
    }
  }

  override def executeAssetsAction(assets: Page[AssetView]): String = {
    runCommandGeneric(templateCommandWithObject(assets) : _*) match {
      case None => ""
      case Some(s: String) => s
      case _ => ""
    }
  }

}
