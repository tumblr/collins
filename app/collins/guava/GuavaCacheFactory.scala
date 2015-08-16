package collins.guava

import com.google.common.cache.CacheBuilder
import com.google.common.cache.CacheLoader
import com.google.common.cache.LoadingCache
import com.google.common.cache.{ Cache => BasicCache }
import com.google.common.cache.{Cache => BasicCache}

/**
 * Creates an instance of Guava cache with the provided specification and cache loader.
 */
object GuavaCacheFactory {
  def create[K <: AnyRef, V <: AnyRef](specification: String, loader: => CacheLoader[K, V]): LoadingCache[K, V] =
    CacheBuilder.from(specification)
      .build(loader)

  def create[K <: AnyRef, V <: AnyRef](specification: String): BasicCache[K, V] = CacheBuilder.from(specification).build()
}