package collins
package callbacks

import java.beans.PropertyChangeEvent

trait CallbackActionHandler {
  def apply(pce: PropertyChangeEvent)

  protected def getValue(pce: PropertyChangeEvent): AnyRef =
    Option(pce.getNewValue).getOrElse(pce.getOldValue)

  protected def getValueOption(pce: PropertyChangeEvent): Option[AnyRef] = Option(getValue(pce))
}
