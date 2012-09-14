package collins
package script

import action.FormattedValues

import play.api.Logger


/**
 * Convenience object for executing command-style CollinScript method calls.
 */
object CollinScriptExecutor {

  protected val logger = Logger("CollinScriptExecutor")

  /**
   * Executes a command stored as a sequence of objects, casting the first
   * argument as a String and passing the rest to the CollinScript verbatim.
   *
   * @param cmd a command to execute by a CollinScript method call.
   * @return the results of the CollinScript method call.
   */
  def runScriptCommand(cmd: Seq[AnyRef]): AnyRef = {
    val methodCall = cmd(0).asInstanceOf[String]
    val methodCallParams = cmd.slice(1, cmd.length)
    CollinScriptRegistry.callMethod(methodCall, methodCallParams : _*)
  }

}
