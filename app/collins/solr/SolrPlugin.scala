package collins.solr

import java.util.Date

import scala.concurrent.Future

import org.apache.solr.client.solrj.SolrServer
import org.apache.solr.client.solrj.embedded.EmbeddedSolrServer
import org.apache.solr.common.SolrInputDocument

import play.api.Application
import play.api.Logger
import play.api.Play.current
import play.api.Plugin
import play.api.libs.concurrent.Akka
import play.api.libs.concurrent.Execution.Implicits.defaultContext

import collins.models.Asset
import collins.models.AssetLog
import collins.solr.CollinsQueryDSL.str2collins
import collins.solr.Solr.AssetSolrDocument
import collins.util.plugins.Callback
import collins.util.views.Formatter

import akka.actor.Props
import akka.routing.FromConfig

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
    val updater = Akka.system.actorOf(Props[AssetSolrUpdater].withRouter(FromConfig()), name = "solr_asset_updater")
    val logUpdater = Akka.system.actorOf(Props[AssetLogSolrUpdater].withRouter(FromConfig()), name = "solr_asset_log_updater")

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

  def populate() = Future {
    _server.map{ server =>
      logger.warn("Repopulating Solr index")
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
            logger.debug(("Indexed %s: %s".format(serializer.docType.name.toLowerCase, items.head.toString)))
          } else {
            logger.info("Indexed %d %ss".format(docs.size, serializer.docType.name.toLowerCase))
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
