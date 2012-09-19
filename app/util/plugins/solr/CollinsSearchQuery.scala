package util
package plugins
package solr

import util.solr._

import models.{Asset, Page, PageParams, SortDirection}
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
case class CollinsSearchQuery(query: SolrExpression, page: PageParams, sortField: String = "TAG") {

  private[this] val logger = Logger("CollinsSearchQuery")

  def getResults(): Either[String, (Seq[AssetView], Long)] = Solr.server.map{server =>
    val q = new SolrQuery
    val queryString = query.toSolrQueryString
    println(queryString)
    logger.debug("SOLR: " + queryString)
    SolrKeyResolver.either(sortField).right.flatMap { sortKey =>
      q.setQuery(queryString)
      q.setStart(page.offset)
      q.setRows(page.size)
      q.addSortField(sortKey.resolvedName, getSortDirection)
      try {
        val response = server.query(q)
        val results = response.getResults
        Right((results.toArray.toSeq.map {
          case doc: SolrDocument => Asset.findByTag(doc.getFieldValue("TAG").toString)
          case other =>
            logger.warn("Got something weird back from Solr %s".format(other.toString))
            None
        }.flatten, results.getNumFound))
      } catch {
        case s: SolrServerException => Left(s.getMessage + "(query %s)".format(queryString))
        case e => Left(e.getMessage)
      }
    }
  }.getOrElse(Left("Solr Plugin not initialized!"))

  def getPage(): Either[String, Page[AssetView]] = getResults().right.map{case (results, total) =>
    Page(results, page.page, page.page * page.size, total)
  }

  protected def getSortDirection() = {
    if (page.sort == SortAsc)
      SolrQuery.ORDER.asc
    else
      SolrQuery.ORDER.desc
  }

}
