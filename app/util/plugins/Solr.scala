package collins.solr

import java.util.Date
import models.Asset
import org.apache.solr.client.solrj.SolrServer
import org.apache.solr.client.solrj.embedded.EmbeddedSolrServer
import org.apache.solr.common.SolrInputDocument
import org.apache.solr.core.CoreContainer
import org.apache.solr.client.solrj.impl.{HttpSolrServer, XMLResponseParser}
import play.api.{Logger, Play}
import play.api.Play.current
import java.net.URL
import java.io.File

object Solr {

  def plugin: Option[SolrPlugin] = Play.maybeApplication.flatMap{_.plugin[SolrPlugin]}.filter{_.enabled}

  protected def inPlugin[A](f: SolrPlugin => A): Option[A] = {
    Play.maybeApplication.flatMap { app =>
      app.plugin[SolrPlugin].map{plugin=>
        f(plugin)
      }
    }
  }

  def populate() = inPlugin {_.populate()}

  def updateAssets(assets: Seq[Asset]) = inPlugin {_.updateAssets(assets, new Date)}

  def updateAsset(asset: Asset){updateAssets(asset :: Nil)}

  def updateAssetByTag(tag: String) = Asset.findByTag(tag).foreach{updateAsset}

  //TODO: Rename
  type AssetSolrDocument = Map[SolrKey, SolrValue]

  def server: Option[SolrServer] = Play.maybeApplication.flatMap { app =>
    app.plugin[SolrPlugin].filter(_.enabled).map{_.server}
  }

  private[solr] def getNewEmbeddedServer = {
    val solrHome = SolrConfig.embeddedSolrHome
    val coreContainer = new CoreContainer(solrHome)
    val file = new File(solrHome, "solr.xml")
    if (!file.exists()) {
      throw new IllegalArgumentException("Could not find solr configuration file in %s".format(solrHome))
    }
    coreContainer.load(solrHome, file)
    Logger.logger.debug("Booting embedded Solr Server with solrhome " + solrHome)
    new EmbeddedSolrServer(coreContainer, "")
  }

  private[solr] def getNewRemoteServer = {
    //out-of-the-box config from solrj wiki
    //http://wiki.apache.org/solr/Solrj#Changing_other_Connection_Settings
    Logger.logger.debug("Using external Solr Server")
    val server = new HttpSolrServer(SolrConfig.externalUrl.get.toString);
    Logger.logger.debug("test")
    server.setSoTimeout(SolrConfig.socketTimeout);
    server.setConnectionTimeout(SolrConfig.connectionTimeout);
    server.setDefaultMaxConnectionsPerHost(SolrConfig.defaultMaxConnectionsPerHost);
    server.setMaxTotalConnections(SolrConfig.maxTotalConnections);
    server.setFollowRedirects(false);
    server.setAllowCompression(true);
    server.setMaxRetries(1);
    server.setParser(new XMLResponseParser());
    server
  }

}
