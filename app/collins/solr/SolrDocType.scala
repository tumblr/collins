package collins.solr

import play.api.Logger

sealed trait SolrDocType {
  def name: String
  def keyResolver: SolrKeyResolver
  
}

case object AssetDocType extends SolrDocType {
  val name = "ASSET"
  val keyResolver = AssetKeyResolver
}

case object AssetLogDocType extends SolrDocType {
  val name = "ASSET_LOG"
  val keyResolver = AssetLogKeyResolver
}
