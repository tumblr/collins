package collins
package script

import action.FormattedValues

import play.api.Logger


/**
 * Convenience object for executing command-style CollinScript method calls.
 */
object CollinScriptExecutor {

  protected val logger = Logger("CollinScriptExecutor")

  def runScriptCommand(cmd: Seq[AnyRef]): AnyRef = {
    val methodCall = cmd(0).asInstanceOf[String]
    val methodCallParams = cmd.slice(1, cmd.length - 1)
    CollinScriptRegistry.callMethod(methodCall, methodCallParams : _*)
  }

}
