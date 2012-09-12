package collins
package action
package handler

import action.ActionExecutor
import callbacks.CallbackHandler

import java.beans.PropertyChangeEvent
import play.api.Logger
import scala.Nothing


trait CallbackActionHandler extends ActionExecutor with CallbackHandler {

  override protected val logger = Logger("CallbackActionHandler") 

  override def apply(pce: PropertyChangeEvent): Unit = {
    val value = getValue(pce.asInstanceOf[PropertyChangeEvent])
    if (value == null) {
      logger.warn("Got no value back to use with command %s"
          .format(commandString))
    }
    runCommandBoolean(getTemplatedCommand(value))
  }

}
