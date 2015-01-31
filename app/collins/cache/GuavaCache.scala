package collins
package cache

import play.api.{Application, PlayException, Plugin}
import com.google.common.cache.{Cache => GuavaCacheImpl, CacheBuilder, CacheStats => GuavaCacheStats}
import java.util.concurrent.{Callable, TimeUnit}

class GuavaCache(override val timeoutInSeconds: java.lang.Integer) extends Cache {

  lazy private[this] val cache: GuavaCacheImpl[String,AnyRef] =
      CacheBuilder.newBuilder()
        .maximumSize(10000)
        .expireAfterWrite(timeoutMs, TimeUnit.MILLISECONDS)
        .build()
  private[this] implicit def f2c[A](f: => A): Callable[A] = new Callable[A] { def call: A = f }

  override def stats() = new GuavaCacheStatsAdapter(cache.stats)
  override def set(key: String, value: AnyRef) {
    cache.put(key, value)
  }

  override def toString(): String =
    "GuavaCache(%d)".format(timeoutInSeconds)

  override def getOrElseUpdate[T <: AnyRef](key: String, op: => T)(implicit m: Manifest[T]): T = {
    val value = cache.get(key, op)
    if (m.runtimeClass.isAssignableFrom(value.getClass))
      value.asInstanceOf[T]
    else
      throw new IllegalArgumentException("%s not assignable to %s".format(
        m.runtimeClass.getClass.toString,
        value.getClass.toString
      ))
  }

  override def get[T <: AnyRef](key: String)(implicit m: Manifest[T]): Option[T] = {
    cache.getIfPresent(key) match {
      case n if n == null => None
      case s if !m.runtimeClass.isAssignableFrom(s.getClass) => None
      case v => Some(v.asInstanceOf[T])
    }
  }

  override def invalidate(key: String) {
    cache.invalidate(key)
  }

  override def clear() {
    cache.invalidateAll()
  }

}
