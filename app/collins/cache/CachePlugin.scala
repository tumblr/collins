package collins.cache

import play.api.Application
import play.api.Plugin

class CachePlugin(app: Application, override val timeoutInSeconds: java.lang.Integer) extends Plugin with Cache {
  def this(app: Application) = this(app, -1)

  protected var underlying: Cache = _

  override def enabled = {
    CacheConfig.pluginInitialize(app.configuration)
    // the cache gets used before the system is initialized
    // This is terrible, a thousand curses upon my head
    ensureCacheInstance()
    CacheConfig.enabled
  }
  override def onStart() {
    ensureCacheInstance()
    clear()
  }
  override def onStop() {
    if (underlying != null) {
      clear()
    }
  }

  override def set(key: String, value: AnyRef) = underlying.set(key, value)
  override def get[T <: AnyRef](key: String)(implicit m: Manifest[T]): Option[T] =
    underlying.get(key)(m)
  override def getOrElseUpdate[T <: AnyRef](key: String, op: => T)(implicit m: Manifest[T]): T =
    underlying.getOrElseUpdate(key, op)(m)
  override def invalidate(key: String) = underlying.invalidate(key)
  override def clear() = underlying.clear()
  override def stats(): CacheStats = underlying.stats()

  protected def getCacheInstance(): Cache = {
    this.getClass.getClassLoader.loadClass(CacheConfig.className)
      .getConstructor(classOf[java.lang.Integer])
      .newInstance(timeoutInSeconds)
      .asInstanceOf[Cache]
  }

  protected def ensureCacheInstance() {
    if (underlying == null) {
      underlying = getCacheInstance()
    }
  }
}

object CachePlugin {
  def getInstance(app: Application, timeout: Int): CachePlugin = {
    val instance = new CachePlugin(app, timeout)
    instance.onStart()
    instance
  }
}
