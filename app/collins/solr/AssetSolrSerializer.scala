package collins.solr

import collins.solr._

import java.util.Date

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
  def serialize(asset: Asset, indexTime: Date): AssetSolrDocument

  val generatedFields: Seq[SolrKey]
}
