package collins.solr

import collins.solr._
import models.Asset

import Solr._

trait AssetSolrSerializer {
  def serialize(asset: Asset): AssetSolrDocument

  val generatedFields: Seq[SolrKey]
}
