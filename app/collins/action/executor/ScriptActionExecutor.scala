package collins
package action
package executor

import script.CollinScriptExecutor

import play.api.Logger


/**
 * An executor which executes Collins actions by calling a CollinScript method.
 *
 * @param command the command to execute via CollinScript call.
 */
trait ScriptActionExecutor extends ActionExecutor {

  /**
   * Returns an AnyRef corresponding to the return value of a CollinScript
   * method call.
   *
   * @param cmd a FormattedValues based upon which to execute a CollinScript
   * method call
   * @return a Boolean corresponding to the return value of the CollinScript
   * call.
   */
  override protected def runCommand(cmd: AnyRef*): AnyRef = {
    CollinScriptExecutor.runScriptCommand(cmd)
  }

  /**
   * Returns a Boolean corresponding to the return value of a CollinScript
   * method call.
   *
   * @param cmd a FormattedValues based upon which to execute a CollinScript
   * method call
   * @return a Boolean corresponding to the return value of the CollinScript
   * call.
   */
  override protected def runCommandBoolean(cmd: FormattedValues): Boolean = {
    val retVal = runCommand(cmd : _*)
    if (retVal == None) {
      false
    } else{
      retVal.asInstanceOf[Boolean]
    }
  }

  /**
   * Returns a String corresponding to the return value of a CollinScript
   * method call.
   *
   * @param cmd a FormattedValues based upon which to execute a CollinScript
   * method call
   * @return a String corresponding to the return value of the CollinScript
   * call.
   */
  override protected def runCommandString(cmd: FormattedValues): String = {
    val retVal = runCommand(cmd : _*)
    if (retVal == None) {
      ""
    } else{
      retVal.toString
    }
  }

}
