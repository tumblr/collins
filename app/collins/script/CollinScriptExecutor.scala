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
    val methodCallParams = cmd.slice(1, cmd.length)
    logger.error("COMMAND: %s".format(cmd))
    logger.error("METHOD CALL: %s".format(methodCall))
    logger.error("METHOD CALL PARAMS: %s".format(methodCallParams))
    CollinScriptRegistry.callMethod(methodCall, methodCallParams : _*)
  }

}
