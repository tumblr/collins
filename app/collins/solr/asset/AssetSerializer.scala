
package collins.solr

import collins.solr._
import util.views.Formatter

import java.util.Date

import models.{Asset, AssetMeta, AssetMetaValue, AssetLog, IpAddresses, IpmiInfo, MetaWrapper, Truthy}
import AssetMeta.ValueType
import AssetMeta.ValueType._

import Solr._
import SolrKeyFlag._


/**
 * asset meta values are all converted into strings with the meta name as the
 * solr key, using group_id to group values in to multi-valued keys
 */
class AssetSerializer extends SolrSerializer[Asset](AssetDocType) {

  val generatedFields = SolrKey("NUM_DISKS", Integer, Dynamic, SingleValued, Sortable) :: SolrKey("KEYS", String, Dynamic, MultiValued, NotSortable) :: Nil

  val res = AssetDocType.keyResolver

  def serialize(asset: Asset, indexTime: Date) = postProcess {
    val opt = Map[SolrKey, Option[SolrValue]](
      res("UPDATED").get -> asset.updated.map{t => SolrStringValue(Formatter.solrDateFormat(t), StrictUnquoted)},
      res("DELETED").get -> asset.deleted.map{t => SolrStringValue(Formatter.solrDateFormat(t), StrictUnquoted)},
      res("STATE").get -> asset.getState.map{s => SolrStringValue(s.name, StrictUnquoted)},
      res("IP_ADDRESS").get -> {
        val a = IpAddresses.findAllByAsset(asset, false)
        if (a.size > 0) {
          val addresses = SolrMultiValue(MultiSet.fromSeq(a.map{a => SolrStringValue(a.dottedAddress, StrictUnquoted)}))
          Some(addresses)
        } else {
          None
        }
      }
    ).collect{case(k, Some(v)) => (k,v)}

    val ipmi: AssetSolrDocument = IpmiInfo.findByAsset(asset).map{ipmi => Map(
      res(IpmiInfo.Enum.IpmiAddress.toString).get -> SolrStringValue(ipmi.dottedAddress, StrictUnquoted)
    )}.getOrElse(Map())
      
    allDocFields(asset.id, indexTime) ++ opt ++ ipmi ++ Map[SolrKey, SolrValue](
      res("ID").get -> SolrIntValue(asset.id.toInt),
      res("TAG").get -> SolrStringValue(asset.tag, StrictUnquoted),
      res("STATUS").get -> SolrStringValue(asset.getStatus.name, StrictUnquoted),
      res("TYPE").get -> SolrStringValue(asset.getType.name, StrictUnquoted),
      res("CREATED").get -> SolrStringValue(Formatter.solrDateFormat(asset.created), StrictUnquoted)
    ) ++ serializeMetaValues(AssetMetaValue.findByAsset(asset, false))
  }

  
  //FIXME: The parsing logic here is duplicated in AssetMeta.validateValue
  def serializeMetaValues(values: Seq[MetaWrapper]): AssetSolrDocument = {
    def process(build: AssetSolrDocument, remain: Seq[MetaWrapper]): AssetSolrDocument = remain match {
      case head :: tail => {
        val newval = head.getValueType() match {
          case Boolean => SolrBooleanValue((new Truthy(head.getValue())).isTruthy)
          case Integer => SolrIntValue(java.lang.Integer.parseInt(head.getValue()))
          case Double => SolrDoubleValue(java.lang.Double.parseDouble(head.getValue()))
          case _ => SolrStringValue(head.getValue(), StrictUnquoted)
        }
        val solrKey = res(head.getName()).get
        val mergedval = build.get(solrKey) match {
          case Some(exist) => exist match {
            case s: SolrSingleValue => SolrMultiValue(MultiSet(s, newval), newval.valueType)
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
    val disks:Option[Tuple2[SolrKey, SolrValue]] = doc.find{case (k,v) => k.name == "DISK_SIZE_BYTES"}.map{case (k,v) => (res("NUM_DISKS").get -> SolrIntValue(v match {
      case s:SolrSingleValue => 1
      case SolrMultiValue(vals, _) => vals.size.toInt
    }))}
    val newFields = List(disks).flatten.toMap
    val almostDone = doc ++ newFields
    val keyList = SolrMultiValue(MultiSet.fromSeq(almostDone.map{case (k,v) => SolrStringValue(k.name, StrictUnquoted)}.toSeq), String)

    val sortKeys = almostDone.map{case(k,v) => k.sortify(v)}.flatten

    almostDone ++ sortKeys + (res("KEYS").get -> keyList)
  }

}
