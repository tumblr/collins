package util.plugins.solr

import util.solr._
import models.Asset

import Solr._

trait AssetSolrSerializer {
  def serialize(asset: Asset): AssetSolrDocument

  val generatedFields: Seq[SolrKey]
}
