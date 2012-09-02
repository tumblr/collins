package util
package solr

import akka.actor._
import akka.util.duration._
import play.api.Logger

import plugins.solr.Solr 
import models.Asset

import java.util.Collections
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import scala.collection.JavaConverters._

/**
 * The SolrUpdater queues asset updates for batch updating.  Most importantly,
 * if it receives multiple requests of the same asset, it will only update the
 * asset once per batch.  This is to avoid reindexing an asset many times
 * during large updates (such as updating lshw/lldp, which triggers dozens of
 * callbacks)
 */
class SolrUpdater extends Actor {

  private[this] val set = Collections.newSetFromMap[Asset](
    new ConcurrentHashMap[Asset,java.lang.Boolean]()
  )
  private[this] val logger = Logger("SolrUpdater")

  //mutex to prevent multiple concurrent scheduler calls
  val scheduled = new AtomicBoolean(false)

  case object Reindex

  /**
   * Note, even though the callback checks if the asset is deleted, we're still
   * gonna get index requests from the delete asset's meta value deletions
   *
   * Note - also we re-fetch the asset from MySQL to avoid a possible race
   * condition where an asset is deleted immediately after it is updated
   */
  def receive = {
    case asset: Asset =>
      if (shouldIndex(asset)) {
        set.add(asset)
      }
      if (scheduled.compareAndSet(false, true)) {
        context.system.scheduler.scheduleOnce(10 milliseconds, self, Reindex)
      }
    case Reindex =>
      if (scheduled.get == true) {
        val toRemove = set.asScala.toSeq
        logger.info("Got Reindex task, working on %d assets".format(toRemove.size))
        Solr.plugin.foreach(_.updateAssets(toRemove))
        set.removeAll(toRemove.asJava)
        scheduled.set(false)
      }
  }

  protected def shouldIndex(asset: Asset): Boolean =
    Asset.findByTag(asset.tag).map(_.deleted.isDefined == false).getOrElse(false)
}
