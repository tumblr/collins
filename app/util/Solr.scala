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

import util.plugins.Callback
import util.views.Formatter

import AssetMeta.ValueType
import AssetMeta.ValueType._





object Solr {

  def plugin: Option[SolrPlugin] = Play.maybeApplication.flatMap{_.plugin[SolrPlugin]}.filter{_.enabled}

  protected def inPlugin(f: SolrPlugin => Unit): Unit = {
    Play.maybeApplication.foreach { app =>
      app.plugin[SolrPlugin].foreach{plugin=>
        f(plugin)
      }
    }
  }

  def populate() = inPlugin {_.populate()}

  def updateAssets(assets: Seq[Asset]) = inPlugin {_.updateAssets(assets)}

  def updateAsset(asset: Asset){updateAssets(asset :: Nil)}

  def updateAssetByTag(tag: String) = Asset.findByTag(tag, false).foreach{updateAsset}

  def removeAssetByTag(tag: String) = inPlugin {_.removeAssetByTag(tag)}
    
  type AssetSolrDocument = Map[SolrKey, SolrValue]

  def prepForInsertion(typedMap: AssetSolrDocument): SolrInputDocument = {
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

  private[solr] def getNewRemoteServer(remoteUrl: String) = {
    //out-of-the-box config from solrj wiki
    Logger.logger.debug("Using external Solr Server")
    val server = new HttpSolrServer(remoteUrl);
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

import Solr._



trait AssetSolrSerializer {
  def serialize(asset: Asset): AssetSolrDocument

  val generatedFields: Seq[SolrKey]
}

/**
 * asset meta values are all converted into strings with the meta name as the
 * solr key, using group_id to group values in to multi-valued keys
 */
class FlatSerializer extends AssetSolrSerializer {

  val generatedFields = SolrKey("NUM_DISKS", Integer, true) :: SolrKey("KEYS", String, true) :: Nil

  def serialize(asset: Asset) = postProcess {
    val opt = Map[SolrKey, Option[SolrValue]](
      SolrKey("UPDATED", String, false) -> asset.updated.map{t => SolrStringValue(Formatter.solrDateFormat(t))},
      SolrKey("DELETED", String, false) -> asset.deleted.map{t => SolrStringValue(Formatter.solrDateFormat(t))},
      SolrKey("IP_ADDRESS", String, false) -> {
        val a = IpAddresses.findAllByAsset(asset, false)
        if (a.size > 0) {
          val addresses = SolrMultiValue(a.map{a => SolrStringValue(a.dottedAddress)})
          Some(addresses)
        } else {
          None
        }
      }
    ).collect{case(k, Some(v)) => (k,v)}
      
    opt ++ Map[SolrKey, SolrValue](
      SolrKey("TAG", String, false) -> SolrStringValue(asset.tag),
      SolrKey("STATUS", Integer, false) -> SolrIntValue(asset.status),
      SolrKey("TYPE", Integer, false) -> SolrIntValue(asset.getType.id),
      SolrKey("CREATED", String, false) -> SolrStringValue(Formatter.solrDateFormat(asset.created))
    ) ++ serializeMetaValues(AssetMetaValue.findByAsset(asset, false))
  }

  
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
        val solrKey = SolrKey(head.getName(), head.getValueType(), true)
        val mergedval = build.get(solrKey) match {
          case Some(exist) => exist match {
            case s: SolrSingleValue => SolrMultiValue(s :: newval :: Nil, newval.valueType)
            case m: SolrMultiValue => m + newval
          }
          case None => newval
        }
        process(build + (solrKey -> mergedval), tail)
      }
      case _ => build
    }
    process(Map(), values)
  }

  def postProcess(doc: AssetSolrDocument): AssetSolrDocument = {
    val disks:Option[Tuple2[SolrKey, SolrValue]] = doc.find{case (k,v) => k.name == "DISK_SIZE_BYTES"}.map{case (k,v) => (SolrKey("NUM_DISKS", Integer, true) -> SolrIntValue(v match {
      case s:SolrSingleValue => 1
      case SolrMultiValue(vals, _) => vals.size
    }))}
    val newFields = List(disks).flatten.toMap
    val almostDone = doc ++ newFields
    val keyList = SolrMultiValue(almostDone.map{case (k,v) => SolrStringValue(k.name)}.toSeq, String)
    almostDone + (SolrKey("KEYS", String, true) -> keyList)
  }

}


/**
 * This class is a full search query, which includes an expression along with
 * sorting and pagination parameters
 */
case class CollinsSearchQuery(query: SolrExpression, page: PageParams, sortField: String = "TAG") {

  def getResults(): Either[String, (Seq[AssetView], Long)] = Solr.server.map{server =>
    val q = new SolrQuery
    val s = query.toSolrQueryString
    Logger.logger.debug("SOLR: " + s)
    q.setQuery(s)
    q.setStart(page.offset)
    q.setRows(page.size)
    q.addSortField(sortField.toUpperCase, (if (page.sort == "ASC") SolrQuery.ORDER.asc else SolrQuery.ORDER.desc))
    val response = server.query(q)
    val results = response.getResults
    Right((results.toArray.toSeq.map{
      case doc: SolrDocument => Asset.findByTag(doc.getFieldValue("TAG").toString)
      case other => {
        Logger.logger.warn("Got something weird back from Solr %s".format(other.toString))
        None
      }
    }.flatten, results.getNumFound))
  }.getOrElse(Left("Solr Plugin not initialized!"))



  def getPage(): Either[String, Page[AssetView]] = getResults().right.map{case (results, total) =>
    Page(results, page.page, page.size * page.page, total)
  }

}
