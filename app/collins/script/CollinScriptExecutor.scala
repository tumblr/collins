package collins
package script

import action.FormattedValues

import play.api.Logger


/**
 * Convenience object for executing command-style CollinScript method calls.
 */
object CollinScriptExecutor {

  /**
   * Executes a command stored as a sequence of objects, casting the first
   * argument as a String and passing the rest to the CollinScript verbatim.
   *
   * @param cmd a command to execute by a CollinScript method call.
   * @return the results of the CollinScript method call.
   */
  def runScriptCommand(cmd: Seq[AnyRef]): Option[AnyRef] = {
    cmd match {
      case Nil => None
      case Seq(methodCall, methodCallParams@_*) => methodCall match {
        case (method: String) => CollinScriptRegistry.callMethod(method,
            methodCallParams : _*)
        case _ => None
      }
    }
  }

}
