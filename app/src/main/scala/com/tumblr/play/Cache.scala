package com.tumblr.play

import play.api.{Application, PlayException, Plugin}

import com.google.common.cache.{Cache => GuavaCache, CacheBuilder, CacheStats => GuavaCacheStats}
import java.util.concurrent.{Callable, TimeUnit}

trait CacheStats {
  def evictionCount(): Long
  def hitCount(): Long
  def hitRate(): Double
  def missCount(): Long
  def missRate(): Double
  def requestCount(): Long
}

// Influenced by com.google.common.cache.Cache and Play Cache
trait Cache {
  def set(key: String, value: AnyRef)
  def get[T <: AnyRef](key: String)(implicit m: Manifest[T]): Option[T]
  def getOrElseUpdate[T <: AnyRef](key: String, op: => T)(implicit m: Manifest[T]): T
  def invalidate(key: String)
  def clear()
  def stats(): CacheStats
}

class CacheStatsAdapter(stats: GuavaCacheStats) extends CacheStats {
  def evictionCount(): Long = stats.evictionCount
  def hitCount(): Long = stats.hitCount
  def hitRate(): Double = stats.hitRate
  def missCount(): Long = stats.missCount
  def missRate(): Double = stats.missRate
  def requestCount(): Long = stats.requestCount
}

class CachePlugin(app: Application, _cache: Option[GuavaCache[String,AnyRef]], _timeoutSeconds: Int) extends Plugin with Cache {

  def this(app: Application) = this(app, None, -1)

  val pluginDisabled = app.configuration.getString("cache.class").filter(_ == "com.tumblr.play.CachePlugin").headOption
  override def enabled = pluginDisabled.isDefined == true
  override def onStart() {
    getTimeoutInSeconds()
  }
  override def onStop() {
    clear()
  }

  lazy private[this] val cache: GuavaCache[String,AnyRef] =
    _cache.getOrElse(
      CacheBuilder.newBuilder()
        .maximumSize(10000)
        .expireAfterWrite(getTimeoutInSeconds(), TimeUnit.SECONDS)
        .build()
    )
  private[this] implicit def f2c[A](f: => A): Callable[A] = new Callable[A] { def call: A = f }

  override def stats() = new CacheStatsAdapter(cache.stats)
  override def set(key: String, value: AnyRef) {
    cache.put(key, value)
  }

  override def getOrElseUpdate[T <: AnyRef](key: String, op: => T)(implicit m: Manifest[T]): T = {
    val value = cache.get(key, op)
    if (m.erasure.isAssignableFrom(value.getClass))
      value.asInstanceOf[T]
    else
      throw new IllegalArgumentException("%s not assignable to %s".format(
        m.erasure.getClass.toString,
        value.getClass.toString
      ))
  }

  override def get[T <: AnyRef](key: String)(implicit m: Manifest[T]): Option[T] = {
    cache.getIfPresent(key) match {
      case n if n == null => None
      case s if !m.erasure.isAssignableFrom(s.getClass) => None
      case v => Some(v.asInstanceOf[T])
    }
  }

  override def invalidate(key: String) {
    cache.invalidate(key)
  }

  override def clear() {
    cache.invalidateAll()
  }

  protected def getTimeoutInSeconds(): Long = {
    val tmp = app.configuration.getString("cache.timeout").getOrElse("10 minutes")
    if (_timeoutSeconds > 0) {
      return _timeoutSeconds;
    }
    try {
      tmp.split(" ") match {
        case Array(duration, unit) =>
          val tu = TimeUnit.valueOf(unit.toUpperCase)
          tu.toSeconds(duration.toLong)
        case Array(duration) => duration.toLong
        case _ => throw new Exception("Valid format is: duration unit (e.g. 10 minutes, 5 seconds, etc)")
      }
    } catch {
      case e =>
        val title = "An invalid cache timeout was specified"
        val description = "%s is invalid: %s".format(tmp, e.getMessage)
        throw PlayException(title, description, Some(e))
    }
  }
}
