package collins
package script

import action.FormattedValues


/**
 * Convenience object for executing command-style CollinScript method calls.
 */
object CollinScriptExecutor {

  def runScriptCommand(cmd: FormattedValues): AnyRef = {
    val methodCall = cmd(0).asInstanceOf[String]
    val methodCallParams = cmd.slice(1, cmd.length - 1)
    CollinScriptRegistry.callMethod(methodCall, methodCallParams)
  }

}
