package collins.callbacks

import java.beans.PropertyChangeEvent

import play.api.Application
import play.api.Logger
import play.api.Play.current
import play.api.Plugin
import play.api.libs.concurrent.Akka

import akka.actor.Props
import akka.routing.FromConfig

object Callback extends AsyncCallbackManager {
  override protected val logger = Logger(getClass)

  override val changeQueue = Akka.system.actorOf(Props(CallbackMessageQueue(pcs)).
      withRouter(FromConfig()), name = "change_queue_processor")

  def setupCallbacks() {
    if (CallbackConfig.enabled) {
      logger.debug("Loading listeners")
      loadListeners()
    }
  }

  def terminateCallbacks() {
    removeListeners()
  }

  override protected def loadListeners(): Unit = {
    CallbackConfig.registry.foreach { descriptor =>
      logger.debug("Loading callback %s".format(descriptor.name))
      setupCallback(descriptor)
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
