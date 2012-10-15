package collins.solr

import collins.solr._

import java.util.Date

import models.Asset

import util.views.Formatter

import Solr._


abstract class SolrSerializer[T](val docType: SolrDocType) {
  def serialize(item: T, indexTime: Date): AssetSolrDocument

  val generatedFields: Seq[SolrKey]

  def allDocFields(id: Long, indexTime: Date): AssetSolrDocument = Map(
    docType.keyResolver("DOC_TYPE").get -> SolrStringValue(AssetDocType.name, StrictUnquoted),
    docType.keyResolver("LAST_INDEXED").get -> SolrStringValue(Formatter.solrDateFormat(indexTime), StrictUnquoted),
    docType.keyResolver("UUID").get -> SolrStringValue(AssetDocType.name + "_" + id.toString, StrictUnquoted)
  )
}
