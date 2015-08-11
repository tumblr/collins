package collins.events

import play.api.Logger

import play.api.libs.json.JsObject
import play.api.libs.json.JsString

import collins.models.asset.AllAttributes

import akka.actor.Actor
import akka.actor.ActorRef

case class Register()
case class UnRegister()
case class Message(category: Category, property: String, asset: AllAttributes)

// TODO: Think about persistent actor
class EventProcessor extends Actor {
  private[this] val logger = Logger(getClass)

  @volatile
  private[this] var listeners = Set[ActorRef]()

  def receive = {
    case Register => {
      logger.trace("Registering a listerner")
      listeners = listeners + sender
    }
    case UnRegister => {
      logger.trace("Unregistering a listener")
      listeners = listeners - sender
    }
    case Message(category, property, asset) =>
      logger.trace("Received a message of type %s for asset with tag %s for property %s".format(category, asset.asset.tag, property))

      val event = Event(JsObject(Seq(
        "category" -> JsString(category.toString),
        "property" -> JsString(property),
        "data" -> asset.toJsValue)))

      listeners.foreach { _ ! event }
  }
}