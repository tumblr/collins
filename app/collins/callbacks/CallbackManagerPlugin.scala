package collins
package callbacks

import action.Action
import action.handler.CallbackActionHandler

import java.beans.{PropertyChangeEvent, PropertyChangeListener, PropertyChangeSupport}
import java.util.concurrent.Executors

import com.twitter.util.{Future, FuturePool}
import play.api.{Application, Configuration, Logger, Plugin}


class CallbackManagerPlugin(app: Application) extends Plugin with CallbackManager {

  protected[this] val executor = Executors.newCachedThreadPool()
  protected[this] val pool = FuturePool(executor)

  override def enabled: Boolean = {
    CallbackConfig.pluginInitialize(app.configuration)
    CallbackConfig.enabled
  }

  // overrides Plugin.onStart
  override def onStart() {
    if (enabled) {
      loadListeners()
    }
  }

  // overrides Plugin.onStop
  override def onStop() {
    removeListeners()
    try executor.shutdown() catch {
      case _ => // swallow this
    }
  }

  override protected def loadListeners(): Unit = {
    CallbackConfig.registry.foreach { callback =>
      setupCallback(callback)
    }
  }

  protected def setupCallback(descriptor: CallbackDescriptor) {
    val eventName = descriptor.on
    val matchCondition = descriptor.matchCondition
    val currentConfigMatches = CallbackMatcher(matchCondition.current, _.getNewValue)
    val previousConfigMatches = CallbackMatcher(matchCondition.previous, _.getOldValue)
    val handlesMatch = Action.getExecutor(descriptor.matchAction)
    on(eventName, new CallbackActionHandler {
      override def apply(pce: PropertyChangeEvent) {
        if (previousConfigMatches(pce) && currentConfigMatches(pce)) {
          handlesMatch(pce)
        }
      }
    })
  }

}
