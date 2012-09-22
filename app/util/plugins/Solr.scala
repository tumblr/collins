package util.plugins.solr

import akka.actor._
import akka.util.duration._

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
import util.solr.{SolrCallbackHandler, SolrConfig, SolrUpdater}
import util.views.Formatter

import AssetMeta.ValueType
import AssetMeta.ValueType._

import java.util.concurrent.atomic.AtomicBoolean

class SolrPlugin(app: Application) extends Plugin {

  private[this] var _server: Option[SolrServer] = None
  private[this] val logger = Logger("SolrPlugin")
  private[this] val populateInProgress = new AtomicBoolean(false)

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
      //server.deleteByQuery( "*:*" );
      if (populateInProgress.compareAndSet(false, true)) {
        try {
          val assets = Asset.findRaw()
          logger.debug("Populating Solr with %d Assets".format(assets.size))
          updateAssets(assets)
        } catch {
          case e =>
            logger.error("Error populating solr index".format(e.getMessage), e)
        } finally {
          populateInProgress.compareAndSet(true, false)
        }
      } else {
        logger.debug("Repopulating Solr already in progress")
      }
    }.getOrElse(logger.warn("attempted to populate solr when no server was initialized"))
  }

  def updateAssets(assets: Seq[Asset]) {
    _server.map{server =>
      logger.debug("Starting Solr prepForInsertion")
      val docs = assets.map{asset => Solr.prepForInsertion(serializer.serialize(asset))}
      if (docs.size > 0) {
        logger.debug("Found %d docs after prepForInsertion".format(docs.size))
        val fuckingJava = new java.util.ArrayList[SolrInputDocument]
        docs.foreach{doc => fuckingJava.add(doc)}
        logger.debug("Finished adding docs to list")
        server.add(fuckingJava)
        logger.debug("Finished adding list to server")
        server.commit()
        logger.debug("Finished commit")
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
