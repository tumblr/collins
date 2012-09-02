package util
package solr

import akka.actor._
import akka.util.duration._

import _root_.util.plugins.solr.Solr 
import models.Asset

/**
 * The SolrUpdater queues asset updates for batch updating.  Most importantly,
 * if it receives multiple requests of the same asset, it will only update the
 * asset once per batch.  This is to avoid reindexing an asset many times
 * during large updates (such as updating lshw/lldp, which triggers dozens of
 * callbacks)
 */
class SolrUpdater extends Actor {

  // FIXME this is not thread safe
  var queue = new collection.mutable.Queue[Asset]

  //mutex to prevent multiple concurrent scheduler calls
  var scheduled = false

  case object Reindex

  /**
   * Note, even though the callback checks if the asset is deleted, we're still
   * gonna get index requests from the delete asset's meta value deletions
   *
   * Note - also we re-fetch the asset from MySQL to avoid a possible race
   * condition where an asset is deleted immediately after it is updated
   */
  def receive = {
    case asset: Asset => if ((!queue.contains(asset)) && Asset.findByTag(asset.tag).map{_.deleted.isDefined == false}.getOrElse(false)) {
      queue += asset
      if (!scheduled) {
        context.system.scheduler.scheduleOnce(10 milliseconds, self, Reindex)
        scheduled = true
      }
    }
    case Reindex => {
      Solr.plugin.foreach{_.updateAssets(queue.toSeq)}
      queue = new collection.mutable.Queue[Asset]
      scheduled = false
    }
  }
}
