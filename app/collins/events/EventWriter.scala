package collins.events

import play.api.Logger
import play.api.libs.json.JsValue

import akka.actor.Actor
import akka.actor.ActorRef
import akka.actor.actorRef2Scala

case class Event(data: JsValue)

// TODO: Think about persistent view
class EventWriter(client: ActorRef, processor: ActorRef) extends Actor {
  private[this] val logger = Logger(getClass)

  override def preStart(): Unit = {
    processor ! Register
  }

  override def postStop(): Unit = {
    processor ! UnRegister
  }

  def receive = {
    case Event(data) => {
      client ! data
    }
  }
}