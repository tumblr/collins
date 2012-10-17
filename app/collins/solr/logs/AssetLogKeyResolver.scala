package collins.solr

import SolrKeyFlag._


import Solr.AssetSolrDocument
import models.AssetMeta.ValueType._

object AssetLogKeyResolver extends SolrKeyResolver {
  val keys = List(
    SolrKey("MESSAGE", String, Static, SingleValued, NotSortable),
    SolrKey("MESSAGE_TYPE", String, Static, SingleValued, Sortable),
    SolrKey("CREATED", String, Static, SingleValued, Sortable),
    SolrKey("ASSET_ID", Integer, Static, SingleValued, Sortable),
    SolrKey("ASSET_TAG", Integer, Static, SingleValued, Sortable)
  )

  def docSpecificKey(rawKey: String): Option[SolrKey] = {
    keys.find{_ matches rawKey}
  }
}
