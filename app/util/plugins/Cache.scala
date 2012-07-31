package util
package plugins

import com.tumblr.play.CachePlugin
import play.api.{Application, Mode}
import java.io.File

object Cache {
  private[this] def getCachePlugin =
    new CachePlugin(new Application(new File("."), this.getClass.getClassLoader, None, Mode.Dev))
  private[this] val cachePlugin = play.api.Play.maybeApplication.map { app =>
    app.plugin[CachePlugin].getOrElse(getCachePlugin)
  }.getOrElse(getCachePlugin)

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
