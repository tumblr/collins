package util
package plugins
package solr

import util.solr._
import util.views.Formatter

import models.{Asset, AssetMeta, AssetMetaValue, IpAddresses, MetaWrapper, Truthy}
import AssetMeta.ValueType
import AssetMeta.ValueType._

import Solr._

/**
 * asset meta values are all converted into strings with the meta name as the
 * solr key, using group_id to group values in to multi-valued keys
 */
class FlatSerializer extends AssetSolrSerializer {

  val generatedFields = SolrKey("NUM_DISKS", Integer, true) :: SolrKey("KEYS", String, true) :: Nil

  def serialize(asset: Asset) = postProcess {
    val opt = Map[SolrKey, Option[SolrValue]](
      SolrKey("UPDATED", String, false) -> asset.updated.map{t => SolrStringValue(Formatter.solrDateFormat(t))},
      SolrKey("DELETED", String, false) -> asset.deleted.map{t => SolrStringValue(Formatter.solrDateFormat(t))},
      SolrKey("IP_ADDRESS", String, false) -> {
        val a = IpAddresses.findAllByAsset(asset, false)
        if (a.size > 0) {
          val addresses = SolrMultiValue(a.map{a => SolrStringValue(a.dottedAddress)})
          Some(addresses)
        } else {
          None
        }
      }
    ).collect{case(k, Some(v)) => (k,v)}
      
    opt ++ Map[SolrKey, SolrValue](
      SolrKey("TAG", String, false) -> SolrStringValue(asset.tag),
      SolrKey("STATUS", Integer, false) -> SolrIntValue(asset.status),
      SolrKey("TYPE", Integer, false) -> SolrIntValue(asset.getType.id),
      SolrKey("CREATED", String, false) -> SolrStringValue(Formatter.solrDateFormat(asset.created))
    ) ++ serializeMetaValues(AssetMetaValue.findByAsset(asset, false))
  }

  
  //FIXME: The parsing logic here is duplicated in AssetMeta.validateValue
  def serializeMetaValues(values: Seq[MetaWrapper]) = {
    def process(build: AssetSolrDocument, remain: Seq[MetaWrapper]): AssetSolrDocument = remain match {
      case head :: tail => {
        val newval = head.getValueType() match {
          case Boolean => SolrBooleanValue((new Truthy(head.getValue())).isTruthy)
          case Integer => SolrIntValue(java.lang.Integer.parseInt(head.getValue()))
          case Double => SolrDoubleValue(java.lang.Double.parseDouble(head.getValue()))
          case _ => SolrStringValue(head.getValue())
        }
        val solrKey = SolrKeyResolver(head.getName()).get
        val mergedval = build.get(solrKey) match {
          case Some(exist) => exist match {
            case s: SolrSingleValue => SolrMultiValue(s :: newval :: Nil, newval.valueType)
            case m: SolrMultiValue => m + newval
          }
          case None => newval
        }
        process(build + (solrKey -> mergedval), tail)
      }
      case _ => build
    }
    process(Map(), values)
  }

  def postProcess(doc: AssetSolrDocument): AssetSolrDocument = {
    val disks:Option[Tuple2[SolrKey, SolrValue]] = doc.find{case (k,v) => k.name == "DISK_SIZE_BYTES"}.map{case (k,v) => (SolrKey("NUM_DISKS", Integer, true) -> SolrIntValue(v match {
      case s:SolrSingleValue => 1
      case SolrMultiValue(vals, _) => vals.size
    }))}
    val newFields = List(disks).flatten.toMap
    val almostDone = doc ++ newFields
    val keyList = SolrMultiValue(almostDone.map{case (k,v) => SolrStringValue(k.name)}.toSeq, String)
    almostDone + (SolrKey("KEYS", String, true) -> keyList)
  }

}
