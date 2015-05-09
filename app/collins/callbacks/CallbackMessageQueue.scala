package collins.callbacks

import java.beans.PropertyChangeSupport

import play.api.Logger

import akka.actor.Actor

case class CallbackMessageQueue(pcs: PropertyChangeSupport) extends Actor {
  private[this] val logger = Logger("CallbackMessageQueue")

  override def receive = {
    case CallbackMessage(name, oldValue, newValue) =>
      pcs.firePropertyChange(name, oldValue, newValue)
  }
}
