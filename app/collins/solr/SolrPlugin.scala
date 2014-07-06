package collins.solr

import akka.actor._
import akka.util.duration._

import java.util.Date

import models.{Asset, AssetFinder, AssetLog, AssetMeta, AssetMetaValue, AssetType, IpAddresses, MetaWrapper, Page, PageParams, Status, Truthy}
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
import Solr.AssetSolrDocument

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

  val assetSerializer = new AssetSerializer
  val assetLogSerializer = new AssetLogSerializer

  //this must be lazy so it gets called after the system exists
  lazy val updater = Akka.system.actorOf(Props[AssetSolrUpdater], name = "solr_asset_updater")
  lazy val logUpdater = Akka.system.actorOf(Props[AssetLogSolrUpdater], name = "solr_asset_log_updater")

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
      Solr.getNewEmbeddedServer
    } else {
      Solr.getNewRemoteServer
    }
    _server = Some(server)
  }
  /**
   * Setup callbacks on all operations that modify asset data, so we can
   * properly reindex the updated asset in Solr
   */
  private def initializeCallbacks() {
    val callback = SolrAssetCallbackHandler(server, updater)
    Callback.on("asset_update", callback)
    Callback.on("asset_create", callback)
    Callback.on("asset_delete", callback)
    Callback.on("asset_purge", callback)
    Callback.on("asset_meta_value_create", callback)
    Callback.on("asset_meta_value_delete", callback)
    Callback.on("ipAddresses_create", callback)
    Callback.on("ipAddresses_update", callback)
    Callback.on("ipAddresses_delete", callback)

    val logCallback = new SolrAssetLogCallbackHandler(server, logUpdater)
    Callback.on("asset_log_create", logCallback)
    Callback.on("asset_log_update", logCallback)
  }

  def populate() = Akka.future { 
    _server.map{ server => 
      val indexTime = new Date

      //Assets
      logger.debug("Populating Solr with Assets")
      val assets = Asset.findRaw()
      updateAssets(assets, indexTime)
      server.deleteByQuery( """SELECT asset WHERE last_indexed < %s""".format(Formatter.solrDateFormat(indexTime)).solr )

      //logs
      logger.debug("Populating Asset Logs")
      val num = assets.map{asset =>
        val logs = AssetLog.findByAsset(asset)
        updateAssetLogs(logs, indexTime, false)
        logs.size
      }.sum
      _server.foreach{_.commit()}
      logger.info("Indexed %d logs".format(num))
      server.deleteByQuery("""SELECT asset_log WHERE last_indexed < %s""".format(Formatter.solrDateFormat(indexTime)).solr)
    }.getOrElse(logger.warn("attempted to populate solr when no server was initialized"))
  }

  def updateItems[T](items: Seq[T], serializer: SolrSerializer[T], indexTime: Date, commit: Boolean = true) {
    _server.map{server =>
      val docs = items.map{item => prepForInsertion(serializer.serialize(item, indexTime))}
      if (docs.size > 0) {
        val fuckingJava = new java.util.ArrayList[SolrInputDocument]
        docs.foreach{doc => fuckingJava.add(doc)}
        server.add(fuckingJava)
        if (commit) {
          server.commit()
          if (items.size == 1) {
            logger.debug(("Indexed %s: %s".format(serializer.docType.name, items.head.toString)))
          } else {
            logger.info("Indexed %d %ss".format(docs.size, serializer.docType.name))
          }
        }
      } else {
        logger.warn("No items to index!")
      }
    }

  }

  def updateAssets(assets: Seq[Asset], indexTime: Date, commit: Boolean = true) {
    updateItems[Asset](assets, assetSerializer, indexTime, commit)
  }

  def updateAssetLogs(logs: Seq[AssetLog], indexTime: Date, commit: Boolean = true) {
    updateItems[AssetLog](logs, assetLogSerializer, indexTime,commit)
  }

  override def onStop() {
    _server.foreach{case s: EmbeddedSolrServer => s.shutdown}
  }

  def prepForInsertion(typedMap: AssetSolrDocument): SolrInputDocument = {
    val input = new SolrInputDocument
    typedMap.foreach{case(key,value) => input.addField(key.resolvedName,value.value)}
    input
  }
}
