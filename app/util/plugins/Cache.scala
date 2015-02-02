package util
package plugins

import collins.cache.{Cache => CacheInterface, CachePlugin}
import play.api.{Application, Mode, Play}
import java.io.File
import play.api.DefaultApplication

object Cache {

  private[this] val cachePlugin: CacheInterface = play.api.Play.maybeApplication.map { app =>
    app.plugin[CachePlugin].getOrElse(createInstance(-1))
  }.getOrElse(createInstance(-1))

  def createInstance(timeoutInSeconds: Int): CacheInterface = {
    val app = try {
      import play.api.Play.current
      current
    } catch {
      case e: Throwable =>
        new DefaultApplication(new File("."), this.getClass.getClassLoader, None, Mode.Dev)
    }
    CachePlugin.getInstance(app, timeoutInSeconds)
  }

  def set(key: String, value: AnyRef) {
    cachePlugin.set(key, value)
  }
  def get[T <: AnyRef](key: String)(implicit m: Manifest[T]): Option[T] = {
    cachePlugin.get[T](key).asInstanceOf[Option[T]]
  }
  def getOrElseUpdate[T <: AnyRef](key: String)(op: => T)(implicit m: Manifest[T]): T = {
    cachePlugin.getOrElseUpdate[T](key, op).asInstanceOf[T]
  }
  def invalidate(key: String) {
    cachePlugin.invalidate(key)
  }
  def clear() {
    cachePlugin.clear()
  }
  def stats() = cachePlugin.stats()
}
