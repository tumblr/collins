package collins.solr

import akka.actor._
import akka.util.duration._

import java.util.Date

import models.{Asset, AssetFinder, AssetMeta, AssetMetaValue, AssetType, IpAddresses, MetaWrapper, Page, PageParams, Status, Truthy}
import models.asset.AssetView
import models.IpmiInfo.Enum._
import models.SortDirection._

import org.apache.solr.client.solrj.{SolrServer, SolrQuery}
import org.apache.solr.client.solrj.embedded.EmbeddedSolrServer
import org.apache.solr.common.{SolrDocument, SolrInputDocument}
import org.apache.solr.core.CoreContainer
import org.apache.solr.client.solrj.impl.{HttpSolrServer, XMLResponseParser}

import play.api.{Application, Logger, Play, PlayException, Plugin}
import play.api.libs.concurrent._
import play.api.libs.concurrent.Akka._
import play.api.Play.current

import util.AttributeResolver
import util.plugins.Callback
import util.views.Formatter

import AssetMeta.ValueType
import AssetMeta.ValueType._

import CollinsQueryDSL._

class SolrPlugin(app: Application) extends Plugin {

  private[this] var _server: Option[SolrServer] = None
  private[this] val logger = Logger("SolrPlugin")

  def server = _server match {
    case Some(server) => server
    case None => throw new RuntimeException("Attempted to get Solr server when no server is initialized")
  }

  override def enabled = {
    SolrConfig.pluginInitialize(app.configuration)
    SolrConfig.enabled
  }

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

      setupServer

      if (SolrConfig.repopulateOnStartup) {
        populate()
      }
      if (SolrConfig.reactToUpdates) {
        initializeCallbacks()
      }
    }
  }

  private def setupServer() {
    val server = if (SolrConfig.useEmbeddedServer) {
      Solr.getNewEmbeddedServer(SolrConfig.embeddedSolrHome)
    } else {
      Solr.getNewRemoteServer(SolrConfig.externalUrl.get)
    }
    _server = Some(server)
  }
  /**
   * Setup callbacks on all operations that modify asset data, so we can
   * properly reindex the updated asset in Solr
   */
  private def initializeCallbacks() {
    val callback = SolrCallbackHandler(server, updater)
    Callback.on("asset_update", callback)
    Callback.on("asset_create", callback)
    Callback.on("asset_delete", callback)
    Callback.on("asset_purge", callback)
    Callback.on("asset_meta_value_create", callback)
    Callback.on("asset_meta_value_delete", callback)
    Callback.on("ipAddresses_create", callback)
    Callback.on("ipAddresses_update", callback)
    Callback.on("ipAddresses_delete", callback)
  }

  def populate() = Akka.future { 
    _server.map{ server => 
      val indexTime = new Date
      logger.debug("Populating Solr with Assets")
      val assets = Asset.findRaw()
      updateAssets(assets, indexTime)
      //TODO: restrict to only deleting assets!!!
      server.deleteByQuery( "last_indexed < %s".format(Formatter.solrDateFormat(indexTime)).solr );
    }.getOrElse(logger.warn("attempted to populate solr when no server was initialized"))
  }

  def updateAsset(asset: Asset) = {
    logger.debug("updating asset " + asset.toString)
    //updateAssets(asset :: Nil)
  }

  def updateAssets(assets: Seq[Asset], indexTime: Date) {
    _server.map{server =>
      val docs = assets.map{asset => Solr.prepForInsertion(serializer.serialize(asset, indexTime))}
      if (docs.size > 0) {
        val fuckingJava = new java.util.ArrayList[SolrInputDocument]
        docs.foreach{doc => fuckingJava.add(doc)}
        server.add(fuckingJava)
        server.commit()
        if (assets.size == 1) {
          logger.debug("Re-indexing asset " + assets.head.toString)
        } else {
          logger.info("Indexed %d assets".format(docs.size))
        }
      } else {
        logger.warn("No assets to index!")
      }
    }
  }

  override def onStop() {
    _server.foreach{case s: EmbeddedSolrServer => s.shutdown}
  }

}
