package models

import conversions._

import org.squeryl.PrimitiveTypeMode._
import org.squeryl.dsl.ast.{BinaryOperatorNodeLogicalBoolean, LogicalBoolean}
import collins.solr._
import IpmiInfo.Enum._

import util.views.Formatter

import java.util.Date

case class AssetFinder(
  tag: Option[String],
  status: Option[Status],
  assetType: Option[AssetType],
  createdAfter: Option[Date],
  createdBefore: Option[Date],
  updatedAfter: Option[Date],
  updatedBefore: Option[Date],
  state: Option[State])
{
  def asLogicalBoolean(a: Asset): LogicalBoolean = {
    val tagBool = tag.map((a.tag === _))
    val statusBool = status.map((a.status === _.id))
    val typeBool = assetType.map((a.asset_type === _.id))
    val createdAfterTs = createdAfter.map((a.created gte _.asTimestamp))
    val createdBeforeTs = createdBefore.map((a.created lte _.asTimestamp))
    val updatedAfterTs = Some((a.updated gte updatedAfter.map(_.asTimestamp).?))
    val updatedBeforeTs = Some((a.updated lte updatedBefore.map(_.asTimestamp).?))
    val stateBool = state.map((a.state === _.id))
    val ops = Seq(tagBool, statusBool, typeBool, createdAfterTs, createdBeforeTs, updatedAfterTs,
      updatedBeforeTs, stateBool).filter(_ != None).map(_.get)
    ops.reduceRight((a,b) => new BinaryOperatorNodeLogicalBoolean(a, b, "and"))
  }

  /**
   * converts the finder into a sequence of key/value tuples, used as part of forwarding searches
   * to remote collins instances (see RemoteAssetFinder for why it's not a map)
   */
  def toSeq: Seq[(String, String)] = {
    val items:Seq[Option[(String, String)]] = (
      tag.map("tag" -> _) ::
      status.map("status" -> _.name) ::
      assetType.map("type" -> _.name) ::
      createdAfter.map(t => "createdAfter" -> Formatter.dateFormat(t)) ::
      createdBefore.map(t => "createdBefore" -> Formatter.dateFormat(t)) ::
      updatedAfter.map(t => "updatedAfter" -> Formatter.dateFormat(t)) ::
      updatedBefore.map(t => "updatedBefore" -> Formatter.dateFormat(t)) ::
      state.map(s => "state" -> s.name) ::
      Nil
    )
    items.flatten
  }

  def toSolrKeyVals = {
    val items = tag.map{t => SolrKeyVal("tag", StringValueFormat.createValueFor(t))} ::
      status.map{t => SolrKeyVal("status" , SolrIntValue(t.id))} ::
      assetType.map(t => SolrKeyVal("assetType" , SolrIntValue(t.id))) ::
      state.map(t => SolrKeyVal("state", SolrIntValue(t.id))) ::
      Nil
    val cOpt = (createdBefore.map{d =>SolrStringValue(Formatter.solrDateFormat(d), StrictUnquoted)}, createdAfter.map{d =>SolrStringValue(Formatter.solrDateFormat(d), StrictUnquoted)}) match {
      case (None, None) => None
      case (bOpt, aOpt) => Some(SolrKeyRange("created", aOpt, bOpt))
    }
    val uOpt = (updatedBefore.map{d =>SolrStringValue(Formatter.solrDateFormat(d), StrictUnquoted)}, updatedAfter.map{d
      =>SolrStringValue(Formatter.solrDateFormat(d), StrictUnquoted)}) match {
      case (None, None) => None
      case (bOpt, aOpt) => Some(SolrKeyRange("updated", aOpt, bOpt))
    }
    (cOpt :: uOpt :: items).flatten
  }

}

object AssetFinder {

  val empty = AssetFinder(None,None,None,None,None,None,None,None)
}
