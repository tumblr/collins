
package collins.solr

import collins.solr._
import util.views.Formatter

import java.util.Date

import models.{Asset, AssetMeta, AssetMetaValue, AssetLog, IpAddresses, IpmiInfo, MetaWrapper, Truthy}
import AssetMeta.ValueType
import AssetMeta.ValueType._

import Solr._
import SolrKeyFlag._


class AssetLogSerializer extends SolrSerializer[AssetLog](AssetLogDocType) {

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
