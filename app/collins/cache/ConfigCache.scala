package collins.cache

import java.util.concurrent.TimeUnit

import com.google.common.cache.CacheBuilder
import com.google.common.cache.CacheLoader
import com.google.common.cache.LoadingCache

/**
 * Creates a cache suitable for configuration file caching.
 *
 * The configuration applied allows a single cache entry that expires after the specified timeout.
 * After the timeout period, the value in the cache will be reloaded via the supplied CacheLoader.
 */
object ConfigCache {
  def create[K <: AnyRef, V <: AnyRef](timeoutMs: Long, loader: => CacheLoader[K,V]): LoadingCache[K,V] =
    CacheBuilder.newBuilder()
      .maximumSize(1)
      .expireAfterWrite(timeoutMs, TimeUnit.MILLISECONDS)
      .build(loader)

  def create[K <: AnyRef, V <: AnyRef](timeoutMs: Long, maximumSize: Int, loader: => CacheLoader[K,V]): LoadingCache[K,V] =
    CacheBuilder.newBuilder()
      .maximumSize(maximumSize)
      .expireAfterWrite(timeoutMs, TimeUnit.MILLISECONDS)
      .build(loader)
}
