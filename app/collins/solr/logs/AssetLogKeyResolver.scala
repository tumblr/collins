package collins.solr

import SolrKeyFlag._


import Solr.AssetSolrDocument
import models.AssetMeta.ValueType._
import models.logs.LogMessageType

object AssetLogKeyResolver extends SolrKeyResolver {

  val messageTypeKey = new SolrKey("MESSAGE_TYPE", String, Static, SingleValued, Sortable, Set("SEVERITY")) with EnumKey {
    def lookupById(id: Int) = try {
      Some(LogMessageType(id).toString)
    } catch {
      case _: Throwable => None
    }

    def lookupByName(name: String) = try {
      Some(LogMessageType.withName(name.toUpperCase).toString)
    } catch {
      case _: Throwable => None
    }
  }

  val keys = List(
    SolrKey("ID", Integer, Static, SingleValued, Sortable),
    SolrKey("MESSAGE", String, Static, SingleValued, NotSortable),
    SolrKey("CREATED", String, Static, SingleValued, Sortable, Set("DATE")),
    SolrKey("ASSET_ID", Integer, Static, SingleValued, Sortable),
    SolrKey("ASSET_TAG", String, Static, SingleValued, Sortable), 
    messageTypeKey
  )

  def docSpecificKey(rawKey: UpperCaseString): Option[SolrKey] = {
    keys.find{_ matches rawKey}
  }
}
