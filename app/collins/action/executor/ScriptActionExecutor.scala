package collins
package action
package executor

import script.CollinScriptExecutor

import play.api.Logger
import scala.collection.JavaConversions._


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
  override protected def runCommandGeneric(cmd: AnyRef*): Option[AnyRef] = {
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
    runCommandGeneric(cmd : _*) match {
      case None => false
      case Some(b: java.lang.Boolean) => b
      case _ => false
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
    runCommandGeneric(cmd : _*) match {
      case None => ""
      case Some(b: String) => b
      case _ => ""
    }
  }

}
