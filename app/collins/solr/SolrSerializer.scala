package collins.solr

import collins.solr._

import java.util.Date

import models.Asset

import util.views.Formatter

import Solr._


abstract class SolrSerializer[T](val docType: SolrDocType) {
  def getFields(item: T, indexTime: Date): AssetSolrDocument

  def getUUID(item: T): Long

  val generatedFields: Seq[SolrKey]

  def allDocFields(item: T, indexTime: Date): AssetSolrDocument = Map(
    docType.keyResolver("DOC_TYPE").get -> SolrStringValue(docType.name, StrictUnquoted),
    docType.keyResolver("LAST_INDEXED").get -> SolrStringValue(Formatter.solrDateFormat(indexTime), StrictUnquoted),
    docType.keyResolver("UUID").get -> SolrStringValue(docType.name + "_" + getUUID(item).toString, StrictUnquoted)
  )

  def sortKeys(doc: AssetSolrDocument): AssetSolrDocument = doc.flatMap{case (k,v) => k.sortify(v)}

  
  def serialize(item: T, indexTime: Date) = {
    val almostDone: AssetSolrDocument = allDocFields(item, indexTime) ++ getFields(item, indexTime)
    almostDone ++ sortKeys(almostDone)
  }
}
