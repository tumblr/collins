package collins
package action
package handler

import action.ActionHandler
import action.executor.{ExecActionExecutor, ScriptActionExecutor}
import callbacks.CallbackHandler

import java.beans.PropertyChangeEvent


/**
 * An ActionHandler subclass which specifies a set of handlers for Collins
 * Actions corresponding to PropertyChangeEvents resulting from a Collins
 * callback.
 *
 * @param command a sequence of Strings specified by the user in a Collins
 * Action configuration.
 */
trait CallbackActionHandler extends ActionHandler with CallbackHandler {

  /**
   * Executes the Collins Action which corresponds to a PropertyChangeEvent
   * resulting from a Callback.
   *
   * @param pce the PropertyChangeEvent to execute the Collins Action against.
   */
  override def apply(pce: PropertyChangeEvent) = {
    val value = getValue(pce.asInstanceOf[PropertyChangeEvent])
    if (value == null) {
      logger.warn("Got no value back to use with command %s"
          .format(commandString))
    }
    runCommandBoolean(templateFormattedValues(value))
  }

}


/**
 * A CallbackActionHandler subclass which executes Collins Actions by way of
 * a system call to the shell.
 *
 * @param command the Collins Action command to execute
 */
case class CallbackExecActionHandler(override val command: Seq[String])
  extends ExecActionExecutor with CallbackActionHandler


/**
 * A CallbackActionHandler subclass which executes Collins Actions by way of
 * a CollinScript method call.
 *
 * @param command the Collins Action command to execute
 */
case class CallbackScriptActionHandler(override val command: Seq[String])
  extends ScriptActionExecutor with CallbackActionHandler
