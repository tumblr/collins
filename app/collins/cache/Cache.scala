package collins.cache

import play.api.Logger

// Influenced by com.google.common.cache.Cache and Play Cache
trait Cache {
  val timeoutInSeconds: java.lang.Integer
  protected[this] val logger = Logger(getClass)

  def set(key: String, value: AnyRef)
  def get[T <: AnyRef](key: String)(implicit m: Manifest[T]): Option[T]
  def getOrElseUpdate[T <: AnyRef](key: String, op: => T)(implicit m: Manifest[T]): T
  def invalidate(key: String)
  def clear()
  def stats(): CacheStats

  protected def timeoutMs(): Long = timeoutInSeconds match {
    case use if use > 0 =>
      use * 1000L
    case other =>
      CacheConfig.timeoutMs
  }
}
