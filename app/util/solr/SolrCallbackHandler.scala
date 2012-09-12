package util
package solr

import collins.callbacks.CallbackActionHandler
import models.{Asset, AssetMetaValue, IpAddresses}

import akka.actor._
import play.api.Logger
import org.apache.solr.client.solrj.SolrServer
import java.beans.PropertyChangeEvent

// creates fire with null old value, some new value
// deletes fire with some old value, null new value
// updates fire with both
case class SolrCallbackHandler(server: SolrServer, updater: ActorRef) extends CallbackActionHandler {

  private[this] val logger = Logger("SolrCallbackHandler")

  override def apply(pce: PropertyChangeEvent) = getValueOption(pce) match {
    case None =>
    case Some(v) =>
      processValue(v)
  }

  protected def processValue(v: AnyRef) = v match {
    case a: Asset => if (a.deleted.isDefined && a.isDecommissioned) {
      removeAssetByTag(a.tag)
    } else {
      updater ! a
    }
    case v: AssetMetaValue =>
      updater ! v.getAsset
    case i: IpAddresses =>
      updater ! i.getAsset
    case s: String =>
      logger.warn("Got purge event for %s".format(s))
      removeAssetByTag(s)
    case o =>
      logger.error("Unknown value in update callback %s".format(maybeNullString(o)))
  }

  private def maybeNullString(s: AnyRef): String = if (s == null) {
    "null"
  } else {
    s.toString
  }

  protected def removeAssetByTag(tag: String) {
    if (tag != "*") {
      server.deleteByQuery("TAG:" + tag)
      server.commit()
      logger.info("Removed asset %s from index".format(tag))
    }
  }

}
