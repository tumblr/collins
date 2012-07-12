package util.plugins

import models.{Asset, AssetFinder, AssetMeta, AssetMetaValue, AssetView, MetaWrapper, PageParams, Truthy}

import org.apache.solr.client.solrj._
import org.apache.solr.client.solrj.embedded.EmbeddedSolrServer
import org.apache.solr.common.SolrInputDocument
import org.apache.solr.core.CoreContainer
import org.apache.solr.client.solrj.impl.{HttpSolrServer, XMLResponseParser}

import play.api.{Application, Configuration, Logger, Play, PlayException, Plugin}

class SolrPlugin(app: Application) extends Plugin {

  private[this] var _server: Option[SolrServer] = None

  def server = _server.get //FIXME: make the thrown exception more descriptive

  private def config = app.configuration.getConfig("solr")

  lazy val solrHome = config.flatMap{_.getString("embeddedSolrHome")}.getOrElse(throw new Exception("No solrHome set!"))
  override lazy val enabled = config.flatMap{_.getBoolean("enabled")}.getOrElse(false)
  lazy val useEmbedded = config.flatMap{_.getBoolean("useEmbedded")}.getOrElse(true)

  val serializer = new FlatSerializer


  override def onStart() {
    if (enabled) {
      _server = Some(if (useEmbedded) {
        System.setProperty("solr.solr.home",solrHome);
        val initializer = new CoreContainer.Initializer();
        val coreContainer = initializer.initialize();
        Logger.logger.debug("Booting embedded Solr Server")
        new EmbeddedSolrServer(coreContainer, "")
      } else {
        //out-of-the-box config from solrj wiki
        val url = app.configuration.getConfig("solr").flatMap{_.getString("externalUrl")}.getOrElse(throw new Exception("Missing required solr.externalUrl"))
        val server = new HttpSolrServer( url );
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
      })
      populate()
    }
  }

  protected def populate() {
    Logger.logger.debug("Populating Solr with Assets")
    Asset.find(PageParams(0,0,"asc"), AssetFinder.empty).items.collect{case a:Asset => a}.foreach{asset => Solr.insert(Solr.prepForInsertion(serializer.serialize(asset)))}
  }


}

object Solr {

  def query(q: SolrQuery) = Nil

  type AssetSolrDocument = Map[String, SolrValue]

  def prepForInsertion(typedMap: AssetSolrDocument): Map[String,Any] = typedMap.map{case(k,v) => (k,v.value)}

  def server: Option[SolrServer] = Play.maybeApplication.flatMap { app =>
    app.plugin[SolrPlugin].filter(_.enabled).map{_.server}
  }

  //NOTE: here we're inserting documents one at a time, which is fine for an
  //embedded server, but if we switch to a standalone server over http, we
  //should batch insert
  def insert(untypedMap: Map[String, Any]) {
    val input = new SolrInputDocument
    untypedMap.foreach{case(key,value) => input.addField(key,value)}
    val fuckingJava = new java.util.ArrayList[SolrInputDocument]
    fuckingJava.add(input)
    server.foreach{_.add(fuckingJava)}


  }


}
import Solr.AssetSolrDocument

sealed trait SolrValue {
  val value: Any
}

trait SolrSingleValue extends SolrValue 
case class SolrIntValue(value: Int) extends SolrSingleValue
case class SolrDoubleValue(value: Double) extends SolrSingleValue
case class SolrStringValue(value: String) extends SolrSingleValue
case class SolrBooleanValue(value: Boolean) extends SolrSingleValue

case class SolrMultiValue(values: Seq[SolrSingleValue]) extends SolrValue {
  def +(v: SolrSingleValue) = this.copy(values = values :+ v)

  lazy val value = values.map{_.value}.toArray
}


trait AssetSolrSerializer {
  def serialize(asset: Asset): AssetSolrDocument
}

/**
 * asset meta values are all converted into strings with the meta name as the
 * solr key, using group_id to group values in to multi-valued keys
 */
class FlatSerializer extends AssetSolrSerializer {

  def serialize(asset: Asset) = Map[String, SolrValue](
    "tag" -> SolrStringValue(asset.tag),
    "status" -> SolrStringValue(asset.getStatusName()),
    "assetType" -> SolrIntValue(asset.getType.id)
  ) ++ serializeMetaValues(AssetMetaValue.findByAsset(asset))

  import AssetMeta.ValueType._
  
  //FIXME: The parsing logic here is duplicated in AssetMeta.validateValue
  def serializeMetaValues(values: Seq[MetaWrapper]) = {
    def process(build: AssetSolrDocument, remain: Seq[MetaWrapper]): AssetSolrDocument = remain match {
      case head :: tail => {
        val newval = head.getValueType() match {
          case Boolean => SolrBooleanValue((new Truthy(head.getValue())).isTruthy)
          case Integer => SolrIntValue(java.lang.Integer.parseInt(head.getValue()))
          case Double => SolrDoubleValue(java.lang.Double.parseDouble(head.getValue()))
          case _ => SolrStringValue(head.getValue())
        }
        val mergedval = build.get(head.getName()) match {
          case Some(exist) => exist match {
            case s: SolrSingleValue => SolrMultiValue(s :: newval :: Nil)
            case m: SolrMultiValue => m + newval
          }
          case None => newval
        }
        process(build + (head.getName() -> mergedval), tail)
      }
      case _ => build
    }
    process(Map(), values)
  }

}


