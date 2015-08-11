package collins.events

import play.api.Logger

import collins.models.asset.AllAttributes

import akka.actor.Actor
import akka.actor.ActorRef

case class Listener(listernerRef: ActorRef)
case class Message(category: Category, property: String, asset: AllAttributes)

class EventWriter extends Actor {
  private[this] val logger = Logger(getClass)

  def receive = {
    case Listener(lr) => logger.trace("Add a listener")
    case m: Message =>
      logger.trace("Received a message of type %s for asset with tag %s for property %s".format(m.category, m.asset.asset.tag, m.property))
      logger.trace("Sent event to MOM")
  }
}