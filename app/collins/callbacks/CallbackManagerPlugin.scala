package collins
package callbacks

import java.beans.{PropertyChangeEvent, PropertyChangeListener, PropertyChangeSupport}
import play.api.{Application, Configuration, Logger, Plugin}
import play.api.Play.current
import play.api.libs.concurrent.Akka
import akka.actor.Props
import akka.routing.FromConfig

class CallbackManagerPlugin(app: Application) extends Plugin with AsyncCallbackManager {
  override protected val logger = Logger("CallbackManagerPlugin")

  //this must be lazy so it gets called after the system exists
  override lazy val changeQueue = Akka.system.actorOf(Props(CallbackMessageQueue(pcs)).
      withRouter(FromConfig()), name = "change_queue_processor")

  override def enabled: Boolean = {
    CallbackConfig.pluginInitialize(app.configuration)
    CallbackConfig.enabled
  }

  // overrides Plugin.onStart
  override def onStart() {
    if (enabled) {
      logger.debug("Loading listeners")
      loadListeners()
    }
  }

  // overrides Plugin.onStop
  override def onStop() {
    removeListeners()
  }

  override protected def loadListeners(): Unit = {
    CallbackConfig.registry.foreach { callback =>
      logger.debug("Loading callback %s".format(callback.name))
      setupCallback(callback)
    }
  }

  protected def setupCallback(descriptor: CallbackDescriptor) {
    val eventName = descriptor.on
    val matchCondition = descriptor.matchCondition
    val currentConfigMatches = CallbackMatcher(matchCondition.current, _.getNewValue)
    val previousConfigMatches = CallbackMatcher(matchCondition.previous, _.getOldValue)
    val handlesMatch = createMatchHandler(descriptor.matchAction)
    logger.debug("Setting up callback %s - %s %s".format(descriptor.name, eventName, handlesMatch))
    on(eventName, new CallbackActionHandler {
      override def apply(pce: PropertyChangeEvent) {
        val prevMatch = previousConfigMatches(pce)
        val curMatch = currentConfigMatches(pce)
        if (prevMatch && curMatch) {
          handlesMatch(pce)
        }
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
