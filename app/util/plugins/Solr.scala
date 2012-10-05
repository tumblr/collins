package collins.solr

import models.Asset

import org.apache.solr.client.solrj.SolrServer
import org.apache.solr.client.solrj.embedded.EmbeddedSolrServer
import org.apache.solr.common.SolrInputDocument
import org.apache.solr.core.CoreContainer
import org.apache.solr.client.solrj.impl.{HttpSolrServer, XMLResponseParser}

import play.api.{Logger, Play}
import play.api.Play.current

import java.net.URL

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

  def updateAssets(assets: Seq[Asset]) = inPlugin {_.updateAssets(assets)}

  def updateAsset(asset: Asset){updateAssets(asset :: Nil)}

  def updateAssetByTag(tag: String) = Asset.findByTag(tag).foreach{updateAsset}

  type AssetSolrDocument = Map[SolrKey, SolrValue]

  def prepForInsertion(typedMap: AssetSolrDocument): SolrInputDocument = {
    if (typedMap(SolrKeyResolver("TAG").get) == SolrStringValue("Tumblr", StrictUnquoted)) {
      println(typedMap.toString)
    }
    val input = new SolrInputDocument
    typedMap.foreach{case(key,value) => input.addField(key.resolvedName,value.value)}
    input
  }

  def server: Option[SolrServer] = Play.maybeApplication.flatMap { app =>
    app.plugin[SolrPlugin].filter(_.enabled).map{_.server}
  }

  private[solr] def getNewEmbeddedServer(solrHome: String) = {
    System.setProperty("solr.solr.home",solrHome) // (╯°□°)╯︵ɐʌɐɾ
    val initializer = new CoreContainer.Initializer()
    val coreContainer = initializer.initialize()
    Logger.logger.debug("Booting embedded Solr Server with solrhome " + solrHome)
    new EmbeddedSolrServer(coreContainer, "")
  }

  private[solr] def getNewRemoteServer(remoteUrl: URL) = {
    //out-of-the-box config from solrj wiki
    Logger.logger.debug("Using external Solr Server")
    val server = new HttpSolrServer(remoteUrl.toString);
    Logger.logger.debug("test")
    server.setSoTimeout(1000);  // socket read timeout
    server.setConnectionTimeout(100);
    server.setDefaultMaxConnectionsPerHost(100);
    server.setMaxTotalConnections(100);
    server.setFollowRedirects(false);  // defaults to false
    // allowCompression defaults to false.
    // Server side must support gzip or deflate for this to have any effect.
    server.setAllowCompression(true);
    server.setMaxRetries(1); // defaults to 0.  > 1 not recommended.
    server.setParser(new XMLResponseParser()); // binary parser is used by default
    server
  }

}
