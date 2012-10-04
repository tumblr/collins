package collins.solr

import collins.solr._
import models.Asset

import Solr._

sealed trait SolrDocType {
  def stringName: String
}
case object AssetDocType extends SolrDocType {
  val stringName = "ASSET"
}
case object AssetLogDocType extends SolrDocType {
  val stringName = "ASSET_LOG"
}

trait AssetSolrSerializer {
  def serialize(asset: Asset): AssetSolrDocument

  val generatedFields: Seq[SolrKey]
}
