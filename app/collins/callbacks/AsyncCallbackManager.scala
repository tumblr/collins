package collins.callbacks

import akka.actor.ActorRef
import akka.actor.actorRef2Scala

trait AsyncCallbackManager extends CallbackManager {
  protected var changeQueue: Option[ActorRef]

  override def fire(propertyName: String, oldValue: CallbackDatumHolder, newValue: CallbackDatumHolder) {
    logger.debug("Async Firing %s".format(propertyName))
    changeQueue.foreach(_ ! CallbackMessage(propertyName, oldValue, newValue))
  }
}
