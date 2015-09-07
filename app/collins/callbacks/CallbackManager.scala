package collins.callbacks

import java.beans.PropertyChangeEvent
import java.beans.PropertyChangeListener
import java.beans.PropertyChangeSupport

import play.api.Logger

trait CallbackManager {
  protected val logger = Logger(getClass)
  protected val pcs = new PropertyChangeSupport(this)

  def fire(propertyName: String, oldValue: CallbackDatumHolder, newValue: CallbackDatumHolder) {
    logger.debug("Firing %s".format(propertyName))
    pcs.firePropertyChange(propertyName, oldValue, newValue)
  }

  def on(propertyName: String, f: CallbackActionHandler) {
    logger.debug("Registering %s".format(propertyName))
    pcs.addPropertyChangeListener(propertyName, new PropertyChangeListener {
      override def propertyChange(pce: PropertyChangeEvent): Unit = f(pce)
    })
  }

  protected def removeListeners() {
    for (listener <- pcs.getPropertyChangeListeners()) pcs.removePropertyChangeListener(listener)
  }
}
