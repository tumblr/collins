package util.plugins.solr

import akka.actor._
import akka.util.duration._

import models.{Asset, AssetFinder, AssetMeta, AssetMetaValue, AssetType, AssetView, IpAddresses, MetaWrapper, Page, PageParams, Status, Truthy}
import models.IpmiInfo.Enum._

import org.apache.solr.client.solrj.{SolrServer, SolrQuery}
import org.apache.solr.client.solrj.embedded.EmbeddedSolrServer
import org.apache.solr.common.{SolrDocument, SolrInputDocument}
import org.apache.solr.core.CoreContainer
import org.apache.solr.client.solrj.impl.{HttpSolrServer, XMLResponseParser}

import play.api.{Application, Configuration, Logger, Play, PlayException, Plugin}
import play.api.libs.concurrent._
import play.api.libs.concurrent.Akka._
import play.api.Play.current

import util.AttributeResolver
import util.plugins.Callback
import util.views.Formatter

import AssetMeta.ValueType
import AssetMeta.ValueType._

class SolrPlugin(app: Application) extends Plugin {

  private[this] var _server: Option[SolrServer] = None

  def server = _server match {
    case Some(server) => server
    case None => throw new RuntimeException("Attempted to get Solr server when no server is initialized")
  }

  private def config = app.configuration.getConfig("solr")

  lazy val solrHome = config.flatMap{_.getString("embeddedSolrHome")}.getOrElse(throw new IllegalArgumentException("No solrHome set!"))
  override lazy val enabled = config.flatMap{_.getBoolean("enabled")}.getOrElse(false)
  lazy val useEmbedded = config.flatMap{_.getBoolean("useEmbeddedServer")}.getOrElse(true)
  lazy val repopulateOnStartup = config.flatMap{_.getBoolean("repopulateOnStartup")}.getOrElse(false)
  lazy val reactToUpdates = config.flatMap{_.getBoolean("reactToUpdates")}.getOrElse(true)
  lazy val remoteUrl: Option[String] = config.flatMap{_.getString("externalUrl")}

  val serializer = new FlatSerializer

  //this must be lazy so it gets called after the system exists
  lazy val updater = Akka.system.actorOf(Props[SolrUpdater], name = "solr_updater")


  override def onStart() {
    if (enabled) {
      System.setProperty("org.apache.commons.logging.Log", "org.apache.commons.logging.impl.SimpleLog");
      System.setProperty("org.apache.commons.logging.simplelog.showdatetime", "true");
      System.setProperty("org.apache.commons.logging.simplelog.log.org.apache.http", "WARN");
      System.setProperty("org.apache.commons.logging.simplelog.log.org.apache.http.wire", "WARN");
      System.setProperty("org.apache.commons.logging.simplelog.http.wire", "WARN");
      System.setProperty("org.apache.commons.logging.simplelog.http.wire.content", "WARN");
      System.setProperty("org.apache.commons.logging.simplelog.log.org.apache.commons.httpclient", "WARN");
      _server = Some(if (useEmbedded) {
        Solr.getNewEmbeddedServer(solrHome)
      } else {
        Solr.getNewRemoteServer(remoteUrl.getOrElse(throw new IllegalArgumentException("Missing required solr.externalUrl")))
      })

      if (repopulateOnStartup) {
        populate()
      }
      if (reactToUpdates) {
        initializeCallbacks()
      }
    }
  }

  /**
   * Setup callbacks on all operations that modify asset data, so we can
   * properly reindex the updated asset in Solr
   */
  private def initializeCallbacks() {
    val callback: java.beans.PropertyChangeEvent => Unit = event => event.getNewValue match {
      case a: Asset => if (a.deleted.isDefined) {
        removeAssetByTag(a.tag) //deletes are soft right now
      } else {
        //Logger.logger.debug("preparing to index asset " + a.toString
        updater ! a
      }
      case v: AssetMetaValue => updater ! v.getAsset
      case i: IpAddresses => updater ! i.getAsset
      case null => event.getOldValue match {
        case a: Asset => removeAssetByTag(a.tag)
        case v: AssetMetaValue => updater ! v.getAsset
        case i: IpAddresses => updater ! i.getAsset
        case other => Logger.logger.error("Unknown old value in update callback %s".format((if (other == null) "null" else other.toString)))
      }
      case other => Logger.logger.error("Unknown new value in update callback %s".format(other.toString))      
    }
    Callback.on("asset_update")(callback)
    Callback.on("asset_create")(callback)
    Callback.on("asset_delete")(callback)
    Callback.on("asset_meta_value_create")(callback)
    Callback.on("asset_meta_value_delete")(callback)
    Callback.on("ipAddresses_create")(callback)
    Callback.on("ipAddresses_update")(callback)
    Callback.on("ipAddresses_delete")(callback)

  }

  def populate() = Akka.future { 
    _server.map{ server => 
      //server.deleteByQuery( "*:*" );
      Logger.logger.debug("Populating Solr with Assets")
      updateAssets(Asset.find(PageParams(0,10000,"asc"), AttributeResolver.emptyResultTuple, AssetFinder.empty).items.collect{case a: Asset => a})
    }.getOrElse(Logger.logger.warn("attempted to populate solr when no server was initialized"))
  }

  def updateAsset(asset: Asset) = {
    Logger.logger.debug("updating asset " + asset.toString)
    //updateAssets(asset :: Nil)
  }

  def updateAssets(assets: Seq[Asset]) {
    _server.map{server =>
      val docs = assets.map{asset => Solr.prepForInsertion(serializer.serialize(asset))}
      if (docs.size > 0) {
        val fuckingJava = new java.util.ArrayList[SolrInputDocument]
        docs.foreach{doc => fuckingJava.add(doc)}
        server.add(fuckingJava)
        server.commit()
        if (assets.size == 1) {
          Logger.logger.debug("Re-indexing asset " + assets.head.toString)
        } else {
          Logger.logger.info("Indexed %d assets".format(docs.size))
        }
      } else {
        Logger.logger.warn("No assets to index!")
      }
    }
  }

  def removeAssetByTag(tag: String) {
    _server.map{server => 
      if (tag != "*") {
        server.deleteByQuery("TAG:" + tag)
        server.commit()
        Logger.logger.info("Removed asset %s from index".format(tag))
      }
    }
  }

  override def onStop() {
    _server.foreach{case s: EmbeddedSolrServer => s.shutdown}
  }



}

/**
 * The SolrUpdater queues asset updates for batch updating.  Most importantly,
 * if it receives multiple requests of the same asset, it will only update the
 * asset once per batch.  This is to avoid reindexing an asset many times
 * during large updates (such as updating lshw/lldp, which triggers dozens of
 * callbacks)
 */
class SolrUpdater extends Actor {

  var queue = new collection.mutable.Queue[Asset]

  //mutex to prevent multiple concurrent scheduler calls
  var scheduled = false

  case object Reindex

  /**
   * Note, even though the callback checks if the asset is deleted, we're still
   * gonna get index requests from the delete asset's meta value deletions
   *
   * Note - also we re-fetch the asset from MySQL to avoid a possible race
   * condition where an asset is deleted immediately after it is updated
   */
  def receive = {
    case asset: Asset => if ((!queue.contains(asset)) && Asset.findByTag(asset.tag).map{_.deleted.isDefined == false}.getOrElse(false)) {
      queue += asset
      if (!scheduled) {
        context.system.scheduler.scheduleOnce(10 milliseconds, self, Reindex)
        scheduled = true
      }
    }
    case Reindex => {
      Solr.plugin.foreach{_.updateAssets(queue.toSeq)}
      queue = new collection.mutable.Queue[Asset]
      scheduled = false
    }
  }
}
