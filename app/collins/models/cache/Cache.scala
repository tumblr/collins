package collins.models.cache

import java.util.concurrent.Callable

import play.api.Logger

import com.google.common.cache.{ Cache => BasicCache }
import com.google.common.cache.{ CacheStats => GuavaCacheStats }

import collins.cache.CacheConfig
import collins.cache.GuavaCacheFactory

trait Stats {
  def evictionCount(): Long
  def hitCount(): Long
  def hitRate(): Double
  def missCount(): Long
  def missRate(): Double
  def requestCount(): Long
}

class CacheStats(stats: GuavaCacheStats) extends Stats {
  def evictionCount(): Long = stats.evictionCount
  def hitCount(): Long = stats.hitCount
  def hitRate(): Double = stats.hitRate
  def missCount(): Long = stats.missCount
  def missRate(): Double = stats.missRate
  def requestCount(): Long = stats.requestCount
}

object DisabledStats extends Stats {
  def evictionCount() = 0
  def hitCount() = 0
  def hitRate() = 0
  def missCount() = 0
  def missRate() = 0
  def requestCount() = 0
}

object Cache {

  private val logger = Logger(getClass)
  private[this] var cache: Option[BasicCache[String, AnyRef]] = None

  def setupCache() {
    logger.trace("Initializing cache")
    if (CacheConfig.enabled) {
      cache = Some(GuavaCacheFactory.create(CacheConfig.specification))
    }
  }

  def stats: Stats = {
    cache.map(underlying => new CacheStats(underlying.stats)).getOrElse(DisabledStats)
  }

  private[models] def invalidate(key: String) {
    logger.trace(s"Invalidating cache key $key")
    cache.map(_.invalidate(key))
  }

  def clear() {
    logger.warn("Clearing cache")
    cache.map(_.invalidateAll())
  }

  private[this] implicit def f2c[T](f: => T): Callable[T] = new Callable[T] { def call: T = f }
  private[models] def get[T <: AnyRef](key: String, loader: => T): T = {
    logger.trace(s"Obtaining cache $key with loader")
    cache.map(_.get(key, loader).asInstanceOf[T]).getOrElse(loader)
  }

  private[models] def get[T <: AnyRef](key: String): Option[T] = {
    logger.trace(s"Obtaining cache $key")
    cache.map(_.getIfPresent(key).asInstanceOf[T]).filter { _ != null }
  }

  private[models] def put[T <: AnyRef](key: String, value: T) {
    logger.trace(s"Setting cache $key")
    cache.map(_.put(key, value))
  }
}