package util.plugins
package solr

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

sealed trait SolrQueryComponent {
  def toSolrQueryString: String

}

sealed trait SolrValue {
  val value: Any
  val valueType: ValueType
  val postfix: String
}

abstract class SolrSingleValue(val postfix: String, val valueType: ValueType) extends SolrValue with SolrQueryComponent{

}


case class SolrIntValue(value: Int) extends SolrSingleValue("_meta_i", Integer) {
  def toSolrQueryString = value.toString
}

case class SolrDoubleValue(value: Double) extends SolrSingleValue("_meta_d", Double) {
  def toSolrQueryString = value.toString
}

case class SolrStringValue(value: String) extends SolrSingleValue("_meta_s", String) {
  def toSolrQueryString = value
}

case class SolrBooleanValue(value: Boolean) extends SolrSingleValue("_meta_b", Boolean) {
  def toSolrQueryString = if (value) "true" else "false"
}

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

/*
 * This is the AST and parser for CQL Expressions are basically just
 * primitive-recursive boolean expressions with a few extra operators for
 * various solr functions.  Some exmaples:
 *
 * key = "somevalue"
 * key1 = "a" AND (key2 = 3 OR key3 = true)
 */
sealed trait SolrExpression extends SolrQueryComponent{ 

  /*
   * Performs type-checking and solr-key name resolution: eg converts foo to
   * foo_meta_i.  Returns Right(expr) with the new resolved expression or
   * Left(msg) if there's an error somewhere
   */
  def typeCheck: Either[String, SolrExpression]
}

abstract class SolrMultiExpr(exprs: Seq[SolrExpression], op: String) extends SolrExpression {

  def toSolrQueryString = exprs.map{_.toSolrQueryString}.mkString(" %s ".format(op))

  def create(exprs: Seq[SolrExpression]): SolrMultiExpr

  def typeCheck = {
    val r = exprs.map{_.typeCheck}.foldLeft(Right(Nil): Either[String, Seq[SolrExpression]]){(build, next) => build match {
      case l@Left(error) => l
      case Right(seq) => next match {
        case Left(error) => Left(error)
        case Right(expr) => Right(expr +: seq)
      }
    }}
    r.right.map{s => create(s)}
  }

}

case class SolrAndOp(exprs: Seq[SolrExpression]) extends SolrMultiExpr(exprs, "AND") {
  def AND(k: SolrKeyVal) = SolrAndOp(this :: k :: Nil)

  def create(exprs: Seq[SolrExpression]) = SolrAndOp(exprs)


}

case class SolrOrOp(exprs: Seq[SolrExpression]) extends SolrMultiExpr(exprs, "OR") {
  def OR(k: SolrKeyVal) = SolrOrOp(this :: k :: Nil)

  def create(exprs: Seq[SolrExpression]) = SolrOrOp(exprs)

}

case class SolrKeyVal(key: String, value: SolrSingleValue) extends SolrExpression {
  def AND(k: SolrKeyVal) = SolrAndOp(this :: k :: Nil)
  def OR(k: SolrKeyVal) = SolrOrOp(this :: k :: Nil)

  def toSolrQueryString = key + ":" + value.toSolrQueryString

  def typeCheck = AssetMeta.findByName(key) match {
    case Some(meta) => if (meta.valueType == value.valueType) {
      Right(SolrKeyVal(key + value.postfix, value))
    } else {
      Left("Key %s expects type %s, got %s".format(key, meta.valueType.toString, value.valueType.toString))
    }
    case None => Left("Unknown Meta tag \"%s\"".format(key))
  }

}

object CollinsQueryDSL {
  class CollinsQueryString(val s: String) {
    lazy val query: SolrExpression = (new CollinsQueryParser).parseQuery(s)
  }
  implicit def str2collins(s: String): CollinsQueryString = new CollinsQueryString(s)
  implicit def collins2str(c: CollinsQueryString): String = c.s
  implicit def int_tuple2keyval(t: Tuple2[String, Int]):SolrKeyVal = SolrKeyVal(t._1, SolrIntValue(t._2))
  implicit def string_tuple2keyval(t: Tuple2[String, String]):SolrKeyVal = SolrKeyVal(t._1, SolrStringValue(t._2))
  implicit def double_tuple2keyval(t: Tuple2[String, Double]):SolrKeyVal = SolrKeyVal(t._1, SolrDoubleValue(t._2))
  implicit def boolean_tuple2keyval(t: Tuple2[String, Boolean]):SolrKeyVal = SolrKeyVal(t._1, SolrBooleanValue(t._2))



}

class CollinsQueryException(m: String) extends PlayException("CQL", m)

class CollinsQueryParser extends JavaTokenParsers {

  def parseQuery(input: String) = parse(expr, input) match {
    case Success(exp, next) => if (next.atEnd) {
      exp
    } else {
      throw new CollinsQueryException("Unexpected stuff after query at position %s: %s".format(next.pos.toString, next.first))
    }
    case Failure(wtf, _) => throw new CollinsQueryException("Error parsing query: %s".format(wtf.toString))
  }

  def expr: Parser[SolrExpression] = orOp

  def orOp          = rep1sep(andOp , "OR") ^^ {i => if (i.tail == Nil) i.head else SolrOrOp(i)}
  def andOp         = rep1sep(simpleExpr , "AND")  ^^ {i => if (i.tail == Nil) i.head else SolrAndOp(i)}
  def simpleExpr    = kv | "(" ~> expr <~ ")" 

  def kv            = ident ~ "=" ~ value ^^{case k ~ "=" ~ v => SolrKeyVal(k,v)}
  def value         = numberValue | stringValue | booleanValue
  def numberValue   = decimalNumber ^^{case n => if (n contains ".") {
    SolrDoubleValue(java.lang.Double.parseDouble(n))
  } else {
    SolrIntValue(java.lang.Integer.parseInt(n))
  }}
  def stringValue   = stringLiteral  ^^ {s => SolrStringValue(s.substring(1,s.length-1))}
  def booleanValue  = ("true" | "false") ^^ {case "true" => SolrBooleanValue(true) case _ =>  SolrBooleanValue(false)}


}


