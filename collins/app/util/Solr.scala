package util.plugins

import models.{Asset, AssetFinder, AssetMeta, AssetMetaValue, AssetView, MetaWrapper, PageParams, Truthy}

import org.apache.solr.client.solrj._
import org.apache.solr.client.solrj.embedded.EmbeddedSolrServer
import org.apache.solr.common.SolrInputDocument
import org.apache.solr.core.CoreContainer
import org.apache.solr.client.solrj.impl.{CommonsHttpSolrServer, XMLResponseParser}

import play.api.{Application, Configuration, Logger, Play, PlayException, Plugin}
import play.api.libs.concurrent._
import play.api.Play.current

import scala.util.parsing.combinator._

class SolrPlugin(app: Application) extends Plugin {

  private[this] var _server: Option[SolrServer] = None

  def server = _server.get //FIXME: make the thrown exception more descriptive

  private def config = app.configuration.getConfig("solr")

  lazy val solrHome = config.flatMap{_.getString("embeddedSolrHome")}.getOrElse(throw new Exception("No solrHome set!"))
  override lazy val enabled = config.flatMap{_.getBoolean("enabled")}.getOrElse(false)
  lazy val useEmbedded = config.flatMap{_.getBoolean("useEmbeddedServer")}.getOrElse(true)
  lazy val repopulateOnStartup = config.flatMap{_.getBoolean("repopulateOnStartup")}.getOrElse(false)

  val serializer = new FlatSerializer


  override def onStart() {
    if (enabled) {
      //System.setProperty("org.apache.commons.logging.Log", "org.apache.commons.logging.impl.SimpleLog");
      //System.setProperty("org.apache.commons.logging.simplelog.showdatetime", "true");
      //System.setProperty("org.apache.commons.logging.simplelog.log.org.apache.http", "WARN");
      //System.setProperty("org.apache.commons.logging.simplelog.log.org.apache.http.wire", "WARN");
      //System.setProperty("org.apache.commons.logging.simplelog.log.org.apache.commons.httpclient", "WARN");
      _server = Some(if (useEmbedded) {
        System.setProperty("solr.solr.home",solrHome);
        val initializer = new CoreContainer.Initializer();
        val coreContainer = initializer.initialize();
        Logger.logger.debug("Booting embedded Solr Server")
        new EmbeddedSolrServer(coreContainer, "")
      } else {
        //out-of-the-box config from solrj wiki
        Logger.logger.debug("Using external Solr Server")
        val url = app.configuration.getConfig("solr").flatMap{_.getString("externalUrl")}.getOrElse(throw new Exception("Missing required solr.externalUrl"))
        val server = new CommonsHttpSolrServer( url );
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
      })
      initialize()
    }
  }

  def initialize() {
    if (repopulateOnStartup) {
      Akka.future {
        populate()
      }
    }

  }

  def populate() { 
    _server.map{ server => 
      //server.deleteByQuery( "*:*" );
      Logger.logger.debug("Populating Solr with Assets")
      val docs = Asset.find(PageParams(0,10000,"asc"), AssetFinder.empty).items.collect{case asset:Asset => Solr.prepForInsertion(serializer.serialize(asset))}
      if (docs.size > 0) {
        val fuckingJava = new java.util.ArrayList[SolrInputDocument]
        docs.foreach{doc => fuckingJava.add(doc)}
        server.add(fuckingJava)
        server.commit()
        Logger.logger.info("Indexed %d assets".format(docs.size))
      } else {
        Logger.logger.warn("No assets to index!")
      }
    }.getOrElse(Logger.logger.warn("attempted to populate solr when no server was initialized"))
  }

  override def onStop() {
    _server.foreach{case s: EmbeddedSolrServer => s.shutdown}
  }


}

object Solr {

  def initialize() {
    Play.maybeApplication.foreach { app =>
      app.plugin[SolrPlugin].foreach{plugin=>
        plugin.initialize()
      }
    }
  }

  def populate() {
    Play.maybeApplication.foreach { app =>
      app.plugin[SolrPlugin].foreach{plugin=>
        plugin.populate()
      }
    }
  }
    

  def query(q: SolrQuery) = Nil

  type AssetSolrDocument = Map[String, SolrValue]

  def prepForInsertion(typedMap: AssetSolrDocument): SolrInputDocument = {
    val untyped = typedMap.map{case(k,v) => (k,v.value)}
    val input = new SolrInputDocument
    untyped.foreach{case(key,value) => input.addField(key,value)}
    input
  }

  def server: Option[SolrServer] = Play.maybeApplication.flatMap { app =>
    app.plugin[SolrPlugin].filter(_.enabled).map{_.server}
  }

  def insert(docs: Seq[SolrInputDocument]) {
    println(docs.size)
    val fuckingJava = new java.util.ArrayList[SolrInputDocument]
    docs.foreach{doc => fuckingJava.add(doc)}
    server.foreach{_.add(fuckingJava)}
  }


}
import Solr.AssetSolrDocument
import AssetMeta.ValueType
import AssetMeta.ValueType._

sealed trait SolrValue {
  val value: Any
  val valueType: ValueType
  val postfix: String
}

abstract class SolrSingleValue(val postfix: String, val valueType: ValueType) extends SolrValue 

case class SolrIntValue(value: Int) extends SolrSingleValue("_meta_i", Integer)
case class SolrDoubleValue(value: Double) extends SolrSingleValue("_meta_d", Double)
case class SolrStringValue(value: String) extends SolrSingleValue("_meta_s", String)
case class SolrBooleanValue(value: Boolean) extends SolrSingleValue("_meta_b", Boolean)

//note, we don't have to bother with checking the types of the contained values
//since that's implicitly handled by AssetMeta
case class SolrMultiValue(values: Seq[SolrSingleValue], valueType: ValueType) extends SolrValue {
  require (values.size > 0, "Cannot create empty multi-value")
  def +(v: SolrSingleValue) = this.copy(values = values :+ v)

  lazy val value = values.map{_.value}.toArray

  lazy val postfix = values.head.postfix

}
object SolrMultiValue {
  def apply(values: Seq[SolrSingleValue]): SolrMultiValue = SolrMultiValue(values, values.headOption.map{_.valueType}.getOrElse(String))
}


trait AssetSolrSerializer {
  def serialize(asset: Asset): AssetSolrDocument
}

/**
 * asset meta values are all converted into strings with the meta name as the
 * solr key, using group_id to group values in to multi-valued keys
 */
class FlatSerializer extends AssetSolrSerializer {

  def serialize(asset: Asset) = {
    val opt = Map[String, Option[SolrValue]](
      "updated" -> asset.updated.map{t => SolrStringValue(t.toString)},
      "deleted" -> asset.deleted.map{t => SolrStringValue(t.toString)}
    ).collect{case(k, Some(v)) => (k,v)}
      
    opt ++ Map[String, SolrValue](
      "tag" -> SolrStringValue(asset.tag),
      "status" -> SolrIntValue(asset.status),
      "assetType" -> SolrIntValue(asset.getType.id),
      "created" -> SolrStringValue(asset.created.toString)
    ) ++ serializeMetaValues(AssetMetaValue.findByAsset(asset))
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
        val solrKey = head.getName() + newval.postfix
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

}

sealed trait SolrExpression
case class SolrAndOp(exprs: Seq[SolrExpression]) extends SolrExpression
case class SolrOrOp(exprs: Seq[SolrExpression]) extends SolrExpression
case class SolrKeyVal(key: String, value: SolrSingleValue) extends SolrExpression

class CollinsQueryParser extends JavaTokenParsers {

  def go(input: String) = parse(expr, input)

  def expr: Parser[SolrExpression] = orOp
  def orOp = rep1sep(andOp , "OR") ^^ {i => if (i.tail == Nil) i.head else SolrOrOp(i)}
  def andOp = rep1sep(simpleExpr , "AND")  ^^ {i => if (i.tail == Nil) i.head else SolrAndOp(i)}
  def simpleExpr = kv | "(" ~> expr <~ ")" 

  def kv = ident ~ "=" ~ value ^^{case k ~ "=" ~ v => SolrKeyVal(k,v)}
  def value: Parser[SolrSingleValue] = intValue | doubleValue | stringValue | booleanValue
  def intValue : Parser[SolrSingleValue]= decimalNumber ^^{case n => SolrDoubleValue(java.lang.Double.parseDouble(n))}
  def doubleValue: Parser[SolrSingleValue] = intValue //FIXME
  def stringValue: Parser[SolrSingleValue] = stringLiteral  ^^ {s => SolrStringValue(s)}
  def booleanValue: Parser[SolrSingleValue] = ("true" | "false") ^^ {case "true" => SolrBooleanValue(true) case _ =>  SolrBooleanValue(false)}


}


