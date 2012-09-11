package collins
package action
package handler

import action.ActionExecutor
import callbacks.CallbackHandler

import java.beans.PropertyChangeEvent
import play.api.Logger


trait CallbackActionHandler extends ActionExecutor with CallbackHandler {

  override protected val logger = Logger("CallbackActionHandler") 

  override def apply(onObj: PropertyChangeEvent) = {
    val value = getValue(onObj)
    if (value == null) {
      logger.warn("Got no value back to use with command %s"
          .format(commandString))
    }
    runCommandBoolean(getTemplatedCommand(value))
  }

}
