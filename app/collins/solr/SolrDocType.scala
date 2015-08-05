package collins.solr

sealed trait SolrDocType {
  def name: String
  def keyResolver: SolrKeyResolver
  def fetchFields: String
}

case object AssetDocType extends SolrDocType {
  val name = "ASSET"
  val keyResolver = AssetKeyResolver
  val fetchFields = "TAG"
}

case object AssetLogDocType extends SolrDocType {
  val name = "ASSET_LOG"
  val keyResolver = AssetLogKeyResolver
  val fetchFields = "ID"
}
