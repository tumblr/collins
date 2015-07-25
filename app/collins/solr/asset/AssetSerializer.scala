package collins.solr

import java.util.Date

import collins.models.Asset
import collins.models.AssetMeta.ValueType.Boolean
import collins.models.AssetMeta.ValueType.Double
import collins.models.AssetMeta.ValueType.Integer
import collins.models.AssetMeta.ValueType.String
import collins.models.AssetMetaValue
import collins.models.IpAddresses
import collins.models.IpmiInfo
import collins.models.MetaWrapper
import collins.models.Truthy
import collins.solr.UpperCaseString.UppercaseString2String
import collins.solr.UpperCaseString.string2UpperCaseString
import collins.util.views.Formatter

import collins.solr.Solr.AssetSolrDocument
import collins.solr.SolrKeyFlag._

/**
 * asset meta values are all converted into strings with the meta name as the
 * solr key, using group_id to group values in to multi-valued keys
 */
object AssetSerializer extends SolrSerializer[Asset](AssetDocType) {

  val generatedFields = SolrKey("NUM_DISKS", Integer, Dynamic, SingleValued, Sortable) :: SolrKey("KEYS", String, Dynamic, MultiValued, NotSortable) :: Nil

  val res = AssetDocType.keyResolver

  def getFields(asset: Asset, indexTime: Date) = postProcess {
    val opt = Map[SolrKey, Option[SolrValue]](
      res("UPDATED").get -> asset.updated.map{t => SolrStringValue(Formatter.solrDateFormat(t), StrictUnquoted)},
      res("DELETED").get -> asset.deleted.map{t => SolrStringValue(Formatter.solrDateFormat(t), StrictUnquoted)},
      res("STATE").get -> asset.state.map{s => SolrStringValue(s.name, StrictUnquoted)},
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

    opt ++ ipmi ++ Map[SolrKey, SolrValue](
      res("ID").get -> SolrIntValue(asset.id.toInt),
      res("TAG").get -> SolrStringValue(asset.tag, StrictUnquoted),
      res("STATUS").get -> SolrStringValue(asset.getStatusName, StrictUnquoted),
      res("TYPE").get -> SolrStringValue(asset.getTypeName, StrictUnquoted),
      res("CREATED").get -> SolrStringValue(Formatter.solrDateFormat(asset.created), StrictUnquoted)
    ) ++ serializeMetaValues(AssetMetaValue.findByAsset(asset, false))
  }

  def getUUID(asset: Asset) = asset.id

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

    //val sortKeys = almostDone.map{case(k,v) => k.sortify(v)}.flatten

    almostDone + (res("KEYS").get -> keyList)
  }

}
