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
import java.util.concurrent.atomic.AtomicReference

//TODO: refactor all this

/**
 * The SolrUpdater queues asset updates for batch updating.  Most importantly,
 * if it receives multiple requests of the same asset, it will only update the
 * asset once per batch.  This is to avoid reindexing an asset many times
 * during large updates (such as updating lshw/lldp, which triggers dozens of
 * callbacks)
 */
class AssetSolrUpdater extends Actor {

  private[this] def newAssetTagSet = Collections.newSetFromMap[String](
    new ConcurrentHashMap[String,java.lang.Boolean]()
  )
  
  private[this] val assetTagsRef = new AtomicReference(newAssetTagSet)
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
      assetTagsRef.get.add(asset.tag)
      if (scheduled.compareAndSet(false, true)) {
        logger.debug("Scheduling update, saw %s".format(asset.tag))
        context.system.scheduler.scheduleOnce(10 milliseconds, self, Reindex)
      } else {
        logger.trace("Not scheduling update, saw %s".format(asset.tag))
      }
    case Reindex =>
      if (scheduled.get == true) {
        val assetTags = assetTagsRef.getAndSet(newAssetTagSet).asScala.toSeq
        val indexTime = new Date
        val assets = assetTags.map(t => Asset.findByTag(t)).flatMap(a => a)
        logger.debug("Got Reindex task, working on %d assets".format(assetTags.size))
        Solr.plugin.foreach(_.updateAssets(assets, indexTime))
        scheduled.set(false)
      }
  }
}

class AssetLogSolrUpdater extends Actor {

  def receive = {
    case log: AssetLog => Solr.plugin.foreach{_.updateAssetLogs(List(log), new Date)}
  }

}
