package collins.solr

import models.{Asset, AssetLog, Page, PageParams, SortDirection}
import models.asset.AssetView

import play.api.Logger
import org.apache.solr.client.solrj.{SolrQuery, SolrServerException}
import org.apache.solr.common.SolrDocument

import Solr._
import SortDirection._

/**
 * This class is a full search query, which includes an expression along with
 * sorting and pagination parameters
 */
abstract class CollinsSearchQuery[T](docType: SolrDocType, query: TypedSolrExpression, page: PageParams) {

  private[this] val logger = Logger("CollinsSearchQuery")

  def getResults(): Either[String, (Seq[T], Long)] = Solr.server.map{server =>
    val q = new SolrQuery
    val queryString = query.toSolrQueryString
    docType.keyResolver.either(page.sortField).right.flatMap{k => if (k.isSortable) Right(k.sortKey) else Left("Cannot sort on " + k.name)}.right.flatMap { sortKey =>
      logger.debug("SOLR: " + queryString + "| sort: " + sortKey.name)
      q.setQuery(queryString)
      q.setStart(page.offset)
      q.setRows(page.size)
      q.addSortField(sortKey.resolvedName, getSortDirection)
      try {
        val response = server.query(q)
        val results = response.getResults
        Right((results.toArray.toSeq.map {
          case doc: SolrDocument => parseDocument(doc)
          case other =>
            logger.warn("Got something weird back from Solr %s".format(other.toString))
            None
        }.flatten, results.getNumFound))
      } catch {
        case e => Left(e.getMessage + "(query %s)".format(queryString))
      }
    }
  }.getOrElse(Left("Solr Plugin not initialized!"))

  def getPage(): Either[String, Page[T]] = getResults().right.map{case (results, total) =>
    Page(results, page.page, page.page * page.size, total)
  }

  protected def getSortDirection() = {
    if (page.sort == SortAsc)
      SolrQuery.ORDER.asc
    else
      SolrQuery.ORDER.desc
  }

  def parseDocument(doc: SolrDocument): Option[T]

}

case class AssetSearchQuery(query: TypedSolrExpression, page: PageParams) extends CollinsSearchQuery[Asset](AssetDocType, query, page) {

  def parseDocument(doc: SolrDocument) = Asset.findByTag(doc.getFieldValue("TAG").toString)

}

case class AssetLogSearchQuery(query: TypedSolrExpression, page: PageParams) extends CollinsSearchQuery[AssetLog](AssetLogDocType, query, page) {

  def parseDocument(doc: SolrDocument) = AssetLog.findById(Integer.parseInt(doc.getFieldValue("ID").toString))

}
