package collins.solr

import akka.actor._
import akka.util.duration._
import play.api.Logger

import models.{Asset, AssetLog}

import java.util.Collections
import java.util.Date
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import scala.collection.JavaConverters._

//TODO: refactor all this

/**
 * The SolrUpdater queues asset updates for batch updating.  Most importantly,
 * if it receives multiple requests of the same asset, it will only update the
 * asset once per batch.  This is to avoid reindexing an asset many times
 * during large updates (such as updating lshw/lldp, which triggers dozens of
 * callbacks)
 */
class AssetSolrUpdater extends Actor {

  private[this] val set = Collections.newSetFromMap[String](
    new ConcurrentHashMap[String,java.lang.Boolean]()
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
        set.add(asset.tag)
        if (scheduled.compareAndSet(false, true)) {
          logger.debug("Scheduling update, saw %s".format(asset.tag))
          context.system.scheduler.scheduleOnce(10 milliseconds, self, Reindex)
        } else {
          logger.trace("Not scheduling update, saw %s".format(asset.tag))
        }
      }
    case Reindex =>
      if (scheduled.get == true) {
        val toRemove = set.asScala.toSeq
        val indexTime = new Date
        val assets = toRemove.map(t => Asset.findByTag(t)).filter(_.isDefined).map(_.get)
        logger.debug("Got Reindex task, working on %d assets, set is %d".format(toRemove.size, set.size))
        Solr.plugin.foreach(_.updateAssets(assets, indexTime))
        set.removeAll(toRemove.asJava)
        logger.debug("Set size now %d".format(set.size))
        scheduled.set(false)
      }
  }

  protected def shouldIndex(asset: Asset): Boolean =
    Asset.findByTag(asset.tag).map { oasset =>
      true
    }.getOrElse(false)
}

class AssetLogSolrUpdater extends Actor {

  def receive = {
    case log: AssetLog => Solr.plugin.foreach{_.updateAssetLogs(List(log), new Date)}
  }

}
