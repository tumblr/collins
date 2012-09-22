package collins.graphs

import models.{PageParams, SortDirection}
import util.plugins.solr._

import play.api.Logger
import com.google.common.cache.CacheLoader

case class MetricsQuery(query: String, metrics: Set[String])
case class MetricsCacheLoader() extends CacheLoader[MetricsQuery, Set[String]] {

  private[this] val logger = Logger("collins.graphs.MetricsCacheLoader")

  override def load(metricsQuery: MetricsQuery): Set[String] = {
    val MetricsQuery(query, metrics) = metricsQuery
    val hasMatch = try {
      solrQueryMatches(query)
    } catch {
      case e =>
        logger.error("Error querying solr for %s".format(query), e)
        false
    }
    if (hasMatch) {
      metrics
    } else {
      Set()
    }
  }

  protected def solrQueryMatches(query: String): Boolean = {
    (new CollinsQueryParser)
      .parseQuery(query)
      .right
      .flatMap(_.typeCheck)
      .fold[Boolean](
        err => {
          logger.warn("Error in type check: %s".format(err))
          false
        },
        expr => {
          val cq = CollinsSearchQuery(expr, PageParams(0, 1, SortDirection.SortAsc))
          cq.getPage().fold(
            err => {
              logger.warn("Error executing CQL query: %s".format(err))
              false
            },
            page => if (page.size > 0) {
              logger.debug("Query (%s) had results".format(query))
              true 
            } else {
              logger.debug("Query (%s) had no results".format(query))
              false
            }
          )
        }
      )
  }

}
