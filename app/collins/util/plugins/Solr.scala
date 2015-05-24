package collins.solr

import java.io.File
import java.util.Date

import org.apache.solr.client.solrj.SolrClient
import org.apache.solr.client.solrj.embedded.EmbeddedSolrServer
import org.apache.solr.client.solrj.impl.HttpSolrClient
import org.apache.solr.client.solrj.impl.XMLResponseParser
import org.apache.solr.core.CoreContainer

import play.api.Logger
import play.api.Play

import collins.models.Asset

object Solr {

  def inPlugin[A](f: SolrPlugin => A): Option[A] = {
    if (SolrConfig.enabled) {
      Play.maybeApplication.flatMap{ _.plugin[SolrPlugin] }.filter{_.enabled}.map{ f(_) }
    } else {
      None
    }
  }

  def populate() = inPlugin {_.populate()}

  def updateAssets(assets: Seq[Asset]) = inPlugin {_.updateAssets(assets, new Date)}

  def updateAsset(asset: Asset){updateAssets(asset :: Nil)}

  def updateAssetByTag(tag: String) = Asset.findByTag(tag).foreach{updateAsset}

  //TODO: Rename
  type AssetSolrDocument = Map[SolrKey, SolrValue]

  private[solr] def getNewEmbeddedServer = {
    val solrHome = SolrConfig.embeddedSolrHome
    val file = new File(solrHome, "solr.xml")
    if (!file.exists()) {
      throw new IllegalArgumentException("Could not find solr configuration file in %s".format(solrHome))
    }
    val coreContainer = CoreContainer.createAndLoad(solrHome, file)
    Logger.logger.debug("Booting embedded Solr Server with solrhome " + solrHome)
    new EmbeddedSolrServer(coreContainer, "collins")
  }

  private[solr] def getNewRemoteServer = {
    //out-of-the-box config from solrj wiki
    //http://wiki.apache.org/solr/Solrj#Changing_other_Connection_Settings
    Logger.logger.debug("Using external Solr Server")
    val server = new HttpSolrClient(SolrConfig.externalUrl.get.toString);
    server.setSoTimeout(SolrConfig.socketTimeout);
    server.setConnectionTimeout(SolrConfig.connectionTimeout);
    server.setDefaultMaxConnectionsPerHost(SolrConfig.defaultMaxConnectionsPerHost);
    server.setMaxTotalConnections(SolrConfig.maxTotalConnections);
    server.setFollowRedirects(false);
    server.setAllowCompression(true);
    server.setParser(new XMLResponseParser());
    server
  }

}
