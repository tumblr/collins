package util.plugins

import models.{Asset, AssetMetaValue, MetaWrapper}

import org.apache.solr.client.solrj._
import org.apache.solr.client.solrj.embedded.EmbeddedSolrServer
import org.apache.solr.core.CoreContainer

import play.api.{Application, Configuration, Logger, PlayException, Plugin}

class SolrPlugin(app: Application) extends Plugin {

  private[this] var _server: Option[SolrServer] = None

  def server = _server.get //FIXME: make the thrown exception more descriptive

  lazy val solrHome = app.configuration.getConfig("solr").flatMap{_.getString("solrHome")}.getOrElse(throw new Exception("No solrHome set!"))
  override lazy val enabled = app.configuration.getConfig("solr").flatMap{_.getBoolean("enabled")}.getOrElse(false)


  override def onStart() {
    if (enabled) {
      System.setProperty("solr.solr.home",solrHome);
      val initializer = new CoreContainer.Initializer();
      val coreContainer = initializer.initialize();
      _server = Some(new EmbeddedSolrServer(coreContainer, ""));
    }
  }


}

object Solr {

  def query(q: SolrQuery) = Nil

  type AssetSolrDocument = Map[String, SolrValue]

}
import Solr.AssetSolrDocument

sealed trait SolrValue

trait SolrSingleValue extends SolrValue
case class SolrIntValue(value: Int) extends SolrSingleValue
case class SolrStringValue(value: String) extends SolrSingleValue
case class SolrBooleanValue(value: Boolean) extends SolrSingleValue

case class SolrMultiValue(values: Seq[SolrSingleValue]) extends SolrValue {
  def +(v: SolrSingleValue) = this.copy(values = values :+ v)
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

  def serializeMetaValues(values: Seq[MetaWrapper]) = {
    def process(build: AssetSolrDocument, remain: Seq[MetaWrapper]): AssetSolrDocument = remain match {
      case head :: tail => {
        val newval = SolrStringValue(head.getValue())
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


