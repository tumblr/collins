package collins.callbacks

import play.api.Logger
import akka.actor._

import java.beans.PropertyChangeSupport

case class CallbackMessageQueue(pcs: PropertyChangeSupport) extends Actor {
  private[this] val logger = Logger("SolrCallbackHandler")

  override def receive = {
    case CallbackMessage(name, oldValue, newValue) =>
      pcs.firePropertyChange(name, oldValue, newValue)
  }
}
