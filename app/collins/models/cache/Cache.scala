package collins.models.cache

import java.util.concurrent.Callable

import play.api.Logger

import com.google.common.cache.{ Cache => BasicCache }

import collins.cache.CacheConfig
import collins.cache.GuavaCacheFactory

object Cache {

  private val logger = Logger(getClass)
  private[this] var cache: Option[BasicCache[String, AnyRef]] = None

  def setupCache() {
    logger.trace("Initializing cache")
    if (CacheConfig.enabled) {
      cache = Some(GuavaCacheFactory.create(CacheConfig.specification))
    }
  }

  private[models] def invalidate(key: String) {
    logger.trace(s"Invalidating cache key $key")
    cache.map(_.invalidate(key))
  }

  private[models] def clear() {
    logger.trace("Clearing cache")
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