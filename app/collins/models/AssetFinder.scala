package collins.models

import java.util.Date

import org.squeryl.PrimitiveTypeMode._
import org.squeryl.dsl.ast.BinaryOperatorNodeLogicalBoolean
import org.squeryl.dsl.ast.LogicalBoolean

import collins.models.conversions.dateToTimestamp
import collins.solr.SolrExpression
import collins.solr.SolrIntValue
import collins.solr.SolrKeyRange
import collins.solr.SolrKeyVal
import collins.solr.SolrStringValue
import collins.solr.StrictUnquoted
import collins.solr.StringValueFormat
import collins.solr.CollinsQueryParser
import collins.solr.AssetDocType
import collins.solr.CQLQuery
import collins.util.views.Formatter

case class AssetFinder(
    tag: Option[String],
    status: Option[Status],
    assetType: Option[AssetType],
    createdAfter: Option[Date],
    createdBefore: Option[Date],
    updatedAfter: Option[Date],
    updatedBefore: Option[Date],
    state: Option[State],
    query: Option[String]) {
  def asLogicalBoolean(a: Asset): LogicalBoolean = {
    val tagBool = tag.map((a.tag === _))
    val statusBool = status.map((a.statusId === _.id))
    val typeBool = assetType.map((a.assetTypeId === _.id))
    val createdAfterTs = createdAfter.map((a.created gte _.asTimestamp))
    val createdBeforeTs = createdBefore.map((a.created lte _.asTimestamp))
    val updatedAfterTs = Some((a.updated gte updatedAfter.map(_.asTimestamp).?))
    val updatedBeforeTs = Some((a.updated lte updatedBefore.map(_.asTimestamp).?))
    val stateBool = state.map((a.stateId === _.id))
    val ops = Seq(tagBool, statusBool, typeBool, createdAfterTs, createdBeforeTs, updatedAfterTs,
      updatedBeforeTs, stateBool).filter(_ != None).map(_.get)
    ops.reduceRight((a, b) => new BinaryOperatorNodeLogicalBoolean(a, b, "and"))
  }

  /**
   * converts the finder into a sequence of key/value tuples, used as part of forwarding searches
   * to remote collins instances (see RemoteAssetFinder for why it's not a map)
   */
  def toSeq: Seq[(String, String)] = {
    val items: Seq[Option[(String, String)]] = (
      tag.map("tag" -> _) ::
      status.map("status" -> _.name) ::
      assetType.map("type" -> _.name) ::
      createdAfter.map(t => "createdAfter" -> Formatter.dateFormat(t)) ::
      createdBefore.map(t => "createdBefore" -> Formatter.dateFormat(t)) ::
      updatedAfter.map(t => "updatedAfter" -> Formatter.dateFormat(t)) ::
      updatedBefore.map(t => "updatedBefore" -> Formatter.dateFormat(t)) ::
      state.map(s => "state" -> s.name) ::
      query.map { "query" -> _ } ::
      Nil)
    items.flatten
  }

  def toSolrKeyVals = {
    val parser = CollinsQueryParser(List(AssetDocType))
    val items = tag.map { t => SolrKeyVal("tag", StringValueFormat.createValueFor(t)) } ::
      status.map { t => SolrKeyVal("status", SolrIntValue(t.id)) } ::
      assetType.map(t => SolrKeyVal("assetType", SolrIntValue(t.id))) ::
      state.map(t => SolrKeyVal("state", SolrIntValue(t.id))) ::
      query.map(q => parser.parseQuery(q) match {
        case Left(_) if Asset.isValidTag(q) => parser.parseQuery("tag=%s".format(q)) match {
          case Left(err: String)  => throw new IllegalArgumentException("Invalid cql %s".format(err))
          case Right(q: CQLQuery) => q.where
        }
        case Left(err: String)  => throw new IllegalArgumentException("Invalid cql %s".format(err))
        case Right(q: CQLQuery) => q.where
      }) ::
      Nil

    val cOpt = (createdBefore.map { d => SolrStringValue(Formatter.solrDateFormat(d), StrictUnquoted) }, createdAfter.map { d => SolrStringValue(Formatter.solrDateFormat(d), StrictUnquoted) }) match {
      case (None, None) => None
      case (bOpt, aOpt) => Some(SolrKeyRange("created", aOpt, bOpt, true))
    }
    val uOpt = (updatedBefore.map { d => SolrStringValue(Formatter.solrDateFormat(d), StrictUnquoted) }, updatedAfter.map { d => SolrStringValue(Formatter.solrDateFormat(d), StrictUnquoted)
    }) match {
      case (None, None) => None
      case (bOpt, aOpt) => Some(SolrKeyRange("updated", aOpt, bOpt, true))
    }
    (cOpt :: uOpt :: items).flatten
  }

}

object AssetFinder {

  val empty = AssetFinder(None, None, None, None, None, None, None, None, None)
}
