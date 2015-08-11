package collins.events

import java.beans.PropertyChangeEvent

import play.api.Logger

import collins.callbacks.CallbackActionHandler
import collins.models.Asset
import collins.models.IpAddresses
import collins.models.asset.AllAttributes

import akka.actor.ActorRef
import akka.actor.actorRef2Scala

case class EventsCallbackHandler(writer: ActorRef) extends CallbackActionHandler {
  private[this] val logger = Logger(getClass)

  override def apply(pce: PropertyChangeEvent) = getValueOption(pce) match {
    case None =>
    case Some(v) =>
      processEvent(pce, v)
  }

  def processEvent(pce: PropertyChangeEvent, v: AnyRef) = v match {
    case a: Asset =>
      writer ! Message(Category.Asset, pce.getPropertyName, AllAttributes.get(a))
    case i: IpAddresses =>
      writer ! Message(Category.IpAddress, pce.getPropertyName, AllAttributes.get(i.getAsset))
    case o =>
      logger.error("Unsupported type in event callback handler %s. Supported types are 'Asset' and 'IpAddresses'".format(maybeNullString(o)))
  }
}