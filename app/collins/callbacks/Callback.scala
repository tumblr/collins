package collins.callbacks

import java.beans.PropertyChangeEvent

import play.api.Logger
import play.api.Play.current
import play.api.libs.concurrent.Akka

import akka.actor.ActorRef
import akka.actor.Props
import akka.routing.FromConfig

object Callback extends AsyncCallbackManager {
  override protected val logger = Logger(getClass)

  override protected[this] var changeQueue: Option[ActorRef] = None

  def setupCallbacks() {
    if (CallbackConfig.enabled) {
      logger.debug("Loading listeners")
      changeQueue = Some(Akka.system.actorOf(Props(CallbackMessageQueue(pcs)).
        withRouter(FromConfig()), name = "change_queue_processor"))
      loadListeners()
    }
  }

  def terminateCallbacks() {
    removeListeners()
  }

  private[this] def loadListeners(): Unit = {
    CallbackConfig.registry.foreach { descriptor =>
      logger.debug("Loading callback %s".format(descriptor.name))
      setupCallback(descriptor)
    }
  }

  protected def setupCallback(descriptor: CallbackDescriptor) {
    val eventName = descriptor.on
    val matchCondition = descriptor.matchCondition
    val currentConfigMatches = CallbackMatcher(matchCondition.current, _.getNewValue.asInstanceOf[CallbackDatumHolder])
    val previousConfigMatches = CallbackMatcher(matchCondition.previous, _.getOldValue.asInstanceOf[CallbackDatumHolder])
    val handlesMatch = descriptor.matchAction
    logger.debug("Setting up callback %s - %s %s".format(descriptor.name, eventName, handlesMatch))
    on(eventName, new CallbackActionHandler {

      override def apply(pce: PropertyChangeEvent) {
        val prevMatch = previousConfigMatches(pce)
        val curMatch = currentConfigMatches(pce)

        logger.debug("Callback invocation : Name %s - On %s - Prev %b -- Curr %b".format(descriptor.name, pce.getPropertyName, prevMatch, curMatch))

        if (prevMatch && curMatch) {
          handlesMatch(pce)
        }
      }
    })
  }
}
