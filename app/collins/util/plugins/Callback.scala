package collins.util.plugins

import java.beans.PropertyChangeEvent

import play.api.Play

import collins.callbacks.CallbackActionHandler
import collins.callbacks.CallbackManager
import collins.callbacks.CallbackManagerPlugin

object Callback extends CallbackManager {
  def pluginEnabled: Option[CallbackManagerPlugin] = {
    Play.maybeApplication.flatMap { app =>
      app.plugin[CallbackManagerPlugin].filter(_.enabled)
    }
  }

  def pluginEnabled[T](fn: CallbackManagerPlugin => T): Option[T] = {
    pluginEnabled.map { p =>
      fn(p)
    }
  }

  override def fire(propertyName: String, oldValue: AnyRef, newValue: AnyRef) {
    pluginEnabled { plugin =>
      logger.debug("app.util.plugins.Callback.fire(%s)".format(propertyName))
      plugin.fire(propertyName, oldValue, newValue)
    }
  }

  def on(propertyName: String)(fn: PropertyChangeEvent => Unit) {
    val cbah = new CallbackActionHandler {
      override def apply(pce: PropertyChangeEvent) = fn(pce)
    }
    on(propertyName, cbah)
  }

  override def on(propertyName: String, f: CallbackActionHandler) {
    pluginEnabled { plugin =>
      plugin.on(propertyName, f)
    }
  }

  override protected def loadListeners() {
    // Load from database or whatever. Need to call myself or figure out a way to give this
    // to the manager plugin
  }
}
