package collins.solr

import java.beans.PropertyChangeEvent

import org.apache.solr.client.solrj.SolrClient

import play.api.Logger

import collins.callbacks.CallbackActionHandler
import collins.callbacks.CallbackDatumHolder
import collins.callbacks.StringDatum

import collins.models.Asset
import collins.models.AssetLog
import collins.models.AssetMetaValue
import collins.models.IpAddresses

import akka.actor.ActorRef
import akka.actor.actorRef2Scala

case class SolrAssetCallbackHandler(server: SolrClient, updater: ActorRef) extends CallbackActionHandler {

  private[this] val logger = Logger("SolrCallbackHandler")

  override def apply(pce: PropertyChangeEvent) = processValue(getValue(pce))

  protected def processValue(v: CallbackDatumHolder) = v match {
    case CallbackDatumHolder(Some(a: Asset)) => if (a.deleted.isDefined && a.isDecommissioned) {
      removeAssetByTag(a.tag)
    } else {
      updater ! a
    }
    case CallbackDatumHolder(Some(v: AssetMetaValue)) =>
      updater ! v.asset
    case CallbackDatumHolder(Some(i: IpAddresses)) =>
      updater ! i.getAsset
    case CallbackDatumHolder(Some(StringDatum(s))) =>
      logger.warn("Got purge event for %s".format(s))
      removeAssetByTag(s)
    case CallbackDatumHolder(Some(x)) =>
      logger.info(f"Unexpected call to solr asset callback handler, ignoring entity event call back for ${x.toString}%s")
    case o =>
      logger.error("Unknown value in update callback %s".format(maybeNullString(o)))
  }

  protected def removeAssetByTag(tag: String) {
    if (tag != "*") {
      server.deleteByQuery("TAG:" + tag)
      server.commit()
      logger.info("Removed asset %s from index".format(tag))
    }
  }
}

case class SolrAssetLogCallbackHandler(server: SolrClient, updater: ActorRef) extends CallbackActionHandler {

  private[this] val logger = Logger("SolrAssetLogCallbackHandler")

  override def apply(pce: PropertyChangeEvent) = processValue(getValue(pce))

  protected def processValue(v: CallbackDatumHolder) = v match {
    case CallbackDatumHolder(Some(l: AssetLog)) => {
      updater ! l
    }
    case ev: CallbackDatumHolder =>
      logger.warn(f"Solr AssetLog Callback Handler - Ignoring entity event call back for ${ev.toString}%s")
    case o =>
      logger.error("Unknown value in update callback %s".format(maybeNullString(o)))
  }
}


