package util
package plugins
package solr

import util.solr._

import models.{Asset, AssetView, Page, PageParams}

import play.api.Logger
import org.apache.solr.client.solrj.SolrQuery
import org.apache.solr.common.SolrDocument

import Solr._

/**
 * This class is a full search query, which includes an expression along with
 * sorting and pagination parameters
 */
case class CollinsSearchQuery(query: SolrExpression, page: PageParams, sortField: String) {

  private[this] val logger = Logger("CollinsSearchQuery")

  def getResults(): Either[String, (Seq[AssetView], Long)] = Solr.server.map{server =>
    val q = new SolrQuery
    val s = query.toSolrQueryString
    logger.debug("SOLR: " + s)
    q.setQuery(s)
    q.setStart(page.offset)
    q.setRows(page.size)
    q.addSortField(sortField.toUpperCase, (if (page.sort == "ASC") SolrQuery.ORDER.asc else SolrQuery.ORDER.desc))
    val response = server.query(q)
    val results = response.getResults
    Right((results.toArray.toSeq.map{
      case doc: SolrDocument => Asset.findByTag(doc.getFieldValue("TAG").toString)
      case other => {
        Logger.logger.warn("Got something weird back from Solr %s".format(other.toString))
        None
      }
    }.flatten, results.getNumFound))
  }.getOrElse(Left("Solr Plugin not initialized!"))



  def getPage(): Either[String, Page[AssetView]] = getResults().right.map{case (results, total) =>
    Page(results, page.page, page.size, total)
  }

}
