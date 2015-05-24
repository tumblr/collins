package collins.solr

import org.apache.solr.client.solrj.SolrQuery
import org.apache.solr.common.SolrDocument

import play.api.Logger

import collins.models.Asset
import collins.models.AssetLog
import collins.models.shared.Page
import collins.models.shared.PageParams
import collins.models.shared.SortDirection.SortAsc
import collins.solr.UpperCaseString.string2UpperCaseString

/**
 * This class is a full search query, which includes an expression along with
 * sorting and pagination parameters
 */
abstract class CollinsSearchQuery[T](docType: SolrDocType, query: TypedSolrExpression, page: PageParams) {

  private[this] val logger = Logger("CollinsSearchQuery")

  def getResults(): Either[String, (Seq[T], Long)] = Solr.inPlugin { plugin => 
    val server = plugin.server
    val q = new SolrQuery
    val queryString = query.toSolrQueryString
    docType.keyResolver.either(page.sortField).right.flatMap{k => k.sortKey.map{Right(_)}.getOrElse(Left("Cannot sort on " + k.name))}.right.flatMap { sortKey =>
      logger.debug("SOLR: " + queryString + "| sort: " + sortKey.name)
      q.setQuery(queryString)
      q.setStart(page.offset)
      q.setRows(page.size)
      q.addSort(new SolrQuery.SortClause(sortKey.resolvedName, getSortDirection))
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
        case e: Throwable => Left(e.getMessage + "(query %s)".format(queryString))
      }
    }
  }.getOrElse(Left("Solr is not initialized!"))

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
