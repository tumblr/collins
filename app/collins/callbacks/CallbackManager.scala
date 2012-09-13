package collins
package callbacks

import java.beans.{PropertyChangeEvent, PropertyChangeListener, PropertyChangeSupport}
import play.api.Logger


trait CallbackManager {

  protected val logger = Logger(getClass)
  protected val pcs = new PropertyChangeSupport(this)

  def fire(propertyName: String, oldValue: AnyRef, newValue: AnyRef) {
    logger.debug("Firing %s".format(propertyName))
    pcs.firePropertyChange(propertyName, oldValue, newValue)
  }

  def on(propertyName: String, f: CallbackHandler) {
    logger.debug("Registering %s".format(propertyName))
    pcs.addPropertyChangeListener(propertyName, new PropertyChangeListener {
      override def propertyChange(pce: PropertyChangeEvent): Unit = f(pce)
    })
  }

  protected def loadListeners(): Unit

  protected def removeListeners() {
    for (listener <- pcs.getPropertyChangeListeners()) pcs.removePropertyChangeListener(listener)
  }

}
