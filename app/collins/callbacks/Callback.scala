package collins
package callbacks

import java.beans.PropertyChangeEvent

trait Callback {
  val propertyName: String
  def handle(pce: PropertyChangeEvent)
}

case class CallbackImpl(propertyName: String, fn: PropertyChangeEvent => Unit) extends Callback {
  override def handle(pce: PropertyChangeEvent) = fn(pce)
}
