package collins.cache

import com.google.common.cache.CacheBuilder
import com.google.common.cache.CacheLoader
import com.google.common.cache.LoadingCache

/**
 * Creates an instance of Guava cache with the provided specification and cache loader.
 */
object GuavaCacheFactory {
  def create[K <: AnyRef, V <: AnyRef](specification: String, loader: => CacheLoader[K, V]): LoadingCache[K, V] =
    CacheBuilder.from(specification)
      .build(loader)
}