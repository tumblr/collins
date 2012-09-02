package collins
package callbacks

import java.beans.{PropertyChangeEvent, PropertyChangeListener, PropertyChangeSupport}
import play.api.{Application, Configuration, Logger, Plugin}
import com.twitter.util.{Future, FuturePool}
import java.util.concurrent.Executors

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
      val cb = createCallback(callback)
      on(cb.propertyName) { pce => cb.handle(pce) }
    }
  }

  protected def createCallback(descriptor: CallbackDescriptor): Callback = {
    val eventName = descriptor.on
    val matchCondition = descriptor.matchCondition
    val currentConfigMatches = CallbackMatcher(matchCondition.current, _.getNewValue)
    val previousConfigMatches = CallbackMatcher(matchCondition.previous, _.getOldValue)
    val handlesMatch = createMatchHandler(descriptor.matchAction)
    CallbackImpl(eventName, { pce =>
      if (previousConfigMatches(pce) && currentConfigMatches(pce)) {
        handlesMatch(pce)
      }
    })
  }

  protected def createMatchHandler(cfg: CallbackAction): CallbackActionHandler = {
    cfg.actionType match {
      case CallbackActionType.Exec =>
        runners.ExecActionRunner(cfg.command)
    }
  }

}
