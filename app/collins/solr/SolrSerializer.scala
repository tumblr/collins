package collins.solr

import java.util.Date

import collins.solr.Solr.AssetSolrDocument
import collins.solr.UpperCaseString.string2UpperCaseString
import collins.util.views.Formatter

abstract class SolrSerializer[T](val docType: SolrDocType) {
  def getFields(item: T, indexTime: Date): AssetSolrDocument

  def getUUID(item: T): Long

  val generatedFields: Seq[SolrKey]

  def allDocFields(item: T, indexTime: Date): AssetSolrDocument = Map(
    docType.keyResolver("DOC_TYPE").get -> SolrStringValue(docType.name, StrictUnquoted),
    docType.keyResolver("LAST_INDEXED").get -> SolrStringValue(Formatter.solrDateFormat(indexTime), StrictUnquoted),
    docType.keyResolver("UUID").get -> SolrStringValue(docType.name + "_" + getUUID(item).toString, StrictUnquoted))

  def sortKeys(doc: AssetSolrDocument): AssetSolrDocument = doc.flatMap { case (k, v) => k.sortify(v) }

  def serialize(item: T, indexTime: Date) = {
    val almostDone: AssetSolrDocument = allDocFields(item, indexTime) ++ getFields(item, indexTime)
    almostDone ++ sortKeys(almostDone)
  }
}
