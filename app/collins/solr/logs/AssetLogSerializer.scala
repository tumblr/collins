
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

  def serialize(log: AssetLog, indexTime: Date): AssetSolrDocument = allDocFields(log.id, indexTime) ++ Map[SolrKey, SolrValue](
    res("MESSAGE").get -> SolrStringValue(log.message, StrictUnquoted),
    res("MESSAGE_TYPE").get -> SolrStringValue(log.message_type.toString, StrictUnquoted)
  )


}
