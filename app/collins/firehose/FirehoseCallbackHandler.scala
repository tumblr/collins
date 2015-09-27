package collins.firehose

import java.beans.PropertyChangeEvent

import play.api.Logger

import collins.callbacks.CallbackDatumHolder
import collins.callbacks.StringDatum
import collins.callbacks.CallbackActionHandler
import collins.models.Asset
import collins.models.AssetMetaValue
import collins.models.IpAddresses
import collins.models.asset.AllAttributes

import akka.actor.ActorRef
import akka.actor.actorRef2Scala

case class FirehoseCallbackHandler(writer: ActorRef) extends CallbackActionHandler {
  private[this] val logger = Logger(getClass)

  override def apply(pce: PropertyChangeEvent) = processValue(pce, getValue(pce))

  protected def processValue(pce: PropertyChangeEvent, v: CallbackDatumHolder) = v match {
    case CallbackDatumHolder(Some(a: Asset))          => writer ! new Event(pce.getPropertyName, Category.Asset, AllAttributes.get(a))
    case CallbackDatumHolder(Some(v: AssetMetaValue)) => writer ! new Event(pce.getPropertyName, Category.Meta, AllAttributes.get(v.asset))
    case CallbackDatumHolder(Some(i: IpAddresses))    => writer ! new Event(pce.getPropertyName, Category.IpAddress, AllAttributes.get(i.getAsset))
    case CallbackDatumHolder(Some(StringDatum(s))) =>
      logger.warn("Got purge event for %s".format(s))
    case CallbackDatumHolder(Some(x)) =>
      logger.info(f"""Unexpected entity to firehose callback handler, ignoring entity event call back for ${x.toString}%s.
         Supported entities are "Asset", "AssetMetaValue", "IpAddresses"""")
    case o =>
      logger.error(f"""Unknown type in firehose callback handler ${maybeNullString(o)}%s
        Supported entities are "Asset", "AssetMetaValue", "IpAddresses"""")
  }
}