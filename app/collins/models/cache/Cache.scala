package collins.models.cache

import java.util.concurrent.Callable

import play.api.Logger

import com.google.common.cache.{ Cache => BasicCache }
import com.google.common.cache.CacheStats
import com.hazelcast.core.IMap
import com.hazelcast.monitor.LocalMapStats

import collins.cache.CacheConfig
import collins.guava.GuavaCacheFactory
import collins.guava.GuavaConfig
import collins.hazelcast.HazelcastConfig
import collins.hazelcast.HazelcastHelper

trait Stats {
  def evictionCount(): Long
  def hitCount(): Long
  def hitRate(): Double
  def missCount(): Long
  def missRate(): Double
  def requestCount(): Long
  def maxGetLatency(): Long
  def maxPutLatency(): Long
}

class GuavaStats(stats: CacheStats) extends Stats {
  def evictionCount(): Long = stats.evictionCount
  def hitCount(): Long = stats.hitCount
  def hitRate(): Double = stats.hitRate
  def missCount(): Long = stats.missCount
  def missRate(): Double = stats.missRate
  def requestCount(): Long = stats.requestCount
  def maxGetLatency(): Long = -1
  def maxPutLatency(): Long = -1
}

class HazelcastStats(stats: LocalMapStats) extends Stats {
  def evictionCount(): Long = -1
  def hitCount(): Long = stats.getHits
  def hitRate(): Double = stats.getHits / stats.getGetOperationCount.doubleValue()
  def missCount(): Long = stats.total - stats.getHits
  def missRate(): Double = (stats.total - stats.getHits) / stats.getGetOperationCount.doubleValue()
  def requestCount(): Long = stats.total
  def maxGetLatency(): Long = stats.getMaxGetLatency
  def maxPutLatency(): Long = stats.getMaxPutLatency
}

object DisabledStats extends Stats {
  def evictionCount() = 0
  def hitCount() = 0
  def hitRate() = 0
  def missCount() = 0
  def missRate() = 0
  def requestCount() = 0
  def maxGetLatency() = 0
  def maxPutLatency() = 0
}

private[cache] trait Cache {
  def terminateCache()
  def stats: Stats
  private[models] def invalidate(key: String)
  def clear()
  private[models] def get[T <: AnyRef](key: String, loader: => T): T
  private[models] def get[T <: AnyRef](key: String): Option[T]
  private[models] def put[T <: AnyRef](key: String, value: T)
}

private[cache] class DistributedCache(cache: IMap[String, AnyRef]) extends Cache {
  private val logger = Logger(getClass)

  def terminateCache() {
    logger.debug("Terminating cache")
    clear()
  }

  def stats: Stats = {
    new HazelcastStats(cache.getLocalMapStats())
  }

  private[models] def invalidate(key: String) {
    logger.trace(s"Invalidating cache key $key")
    cache.remove(key)
  }

  def clear() {
    logger.debug("Clearing cache")
    cache.evictAll()
  }

  private[models] def get[T <: AnyRef](key: String, loader: => T): T = {
    logger.trace(s"Obtaining cache $key with loader")

    get(key) match {
      case None => Option(loader) match {
        case Some(v) => {
          put(key, v)
          Some(v)
        }
        case None => None
      }
      case r => r
    }
  }.getOrElse(loader)

  private[models] def get[T <: AnyRef](key: String): Option[T] = {
    logger.trace(s"Obtaining cache $key")
    Option(cache.get(key).asInstanceOf[T])
  }

  private[models] def put[T <: AnyRef](key: String, value: T) {
    logger.trace(s"Setting cache $key")
    cache.put(key, value)
  }
}

private[cache] class InMemoryCache(val cache: BasicCache[String, AnyRef]) extends Cache {
  private val logger = Logger(getClass)
  logger.trace("Initializing InMemory cache")

  def stats: Stats = {
    new GuavaStats(cache.stats)
  }

  def terminateCache() {
    clear()
  }

  private[models] def invalidate(key: String) {
    logger.trace(s"Invalidating cache key $key")
    cache.invalidate(key)
  }

  def clear() {
    logger.warn("Clearing cache")
    cache.invalidateAll()
  }

  private[this] implicit def f2c[T](f: => T): Callable[T] = new Callable[T] { def call: T = f }
  private[models] def get[T <: AnyRef](key: String, loader: => T): T = {
    logger.trace(s"Obtaining cache $key with loader")
    Option(cache.get(key, loader)).getOrElse(loader).asInstanceOf[T]
  }

  private[models] def get[T <: AnyRef](key: String): Option[T] = {
    logger.trace(s"Obtaining cache $key")
    Option(cache.getIfPresent(key).asInstanceOf[T])
  }

  private[models] def put[T <: AnyRef](key: String, value: T) {
    logger.trace(s"Setting cache $key")
    cache.put(key, value)
  }
}

object Cache extends Cache {

  private val logger = Logger(getClass)
  @volatile private[this] var cache: Option[Cache] = None

  def setupCache() {
    logger.debug("Initializing cache")
    if (CacheConfig.enabled) {

      if (CacheConfig.cacheType == "distributed") {
        // SANITY CHECK, this is verified in CacheConfig
        if (!HazelcastConfig.enabled) {
          throw new IllegalStateException("Distributed cache uses hazelcast, please enable and configure it.")
        }
        logger.trace("Instantiating distributed hazelcast cache")
        cache = HazelcastHelper.getCache().map(new DistributedCache(_))
        if (!cache.isDefined) {
          logger.warn("Hazelcast intialization may have failed, expecting an instance got none.")
        } else {
          logger.trace("Instantiated distributed hazelcast cache")
        }
      } else {
        logger.trace("Instantiating in-memory guava cache")
        cache = Some(new InMemoryCache(GuavaCacheFactory.create[String, AnyRef](GuavaConfig.specification)))
        logger.trace("Instantiated in-memory guava cache")
      }
    }
  }

  def terminateCache() {
    cache.foreach(_.terminateCache())
    cache = None
  }
  def stats: Stats = {
    cache.map(_.stats).getOrElse(DisabledStats)
  }

  private[models] def invalidate(key: String) {
    cache.foreach(_.invalidate(key))
  }
  def clear() {
    cache.foreach(_.clear())
  }
  private[models] def get[T <: AnyRef](key: String, loader: => T): T = {
    cache.map { _.get(key, loader) }.getOrElse(loader)
  }

  private[models] def get[T <: AnyRef](key: String): Option[T] = {
    cache.flatMap { _.get(key) }
  }
  private[models] def put[T <: AnyRef](key: String, value: T) {
    cache.foreach { _.put(key, value) }
  }

}