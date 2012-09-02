package util
package plugins

import play.api.{Play, Plugin}
import collins.callbacks.{CallbackManagerPlugin, CallbackManager}
import java.beans.PropertyChangeEvent

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
      plugin.fire(propertyName, oldValue, newValue)
    }
  }

  override def on(propertyName: String)(f: PropertyChangeEvent => Unit) {
    pluginEnabled { plugin =>
      plugin.on(propertyName)(f)
    }
  }

  override protected def loadListeners() {
    // Load from database or whatever. Need to call myself or figure out a way to give this
    // to the manager plugin
  }
}
