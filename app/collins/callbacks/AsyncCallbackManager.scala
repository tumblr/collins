package collins.callbacks

import akka.actor._

trait AsyncCallbackManager extends CallbackManager {
  protected def changeQueue: ActorRef

  override def fire(propertyName: String, oldValue: AnyRef, newValue: AnyRef) {
    logger.debug("Async Firing %s".format(propertyName))
    changeQueue ! CallbackMessage(propertyName, oldValue, newValue)
  }
}
