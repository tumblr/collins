
package collins.solr

import java.util.Date

import collins.models.AssetLog
import collins.solr.Solr.AssetSolrDocument
import collins.solr.UpperCaseString.string2UpperCaseString
import collins.util.views.Formatter

object AssetLogSerializer extends SolrSerializer[AssetLog](AssetLogDocType) {

  val generatedFields = Nil

  val res = AssetLogDocType.keyResolver

  def getFields(log: AssetLog, indexTime: Date): AssetSolrDocument = Map[SolrKey, SolrValue](
    res("ID").get -> SolrIntValue(log.id.toInt),
    res("MESSAGE").get -> SolrStringValue(log.message, StrictUnquoted),
    res("MESSAGE_TYPE").get -> SolrStringValue(log.message_type.toString, StrictUnquoted),
    res("ASSET_TAG").get -> SolrStringValue(log.getAssetTag()),
    res("CREATED").get -> SolrStringValue(Formatter.solrDateFormat(log.created))
  )

  def getUUID(log: AssetLog) = log.id


}
