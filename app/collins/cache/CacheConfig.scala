package collins
package cache

import util.config.Configurable
import play.api.Logger

object CacheConfig extends Configurable {
  private[this] val logger = Logger("CacheConfig")

  override val namespace = "cache"
  override val referenceConfigFilename = "cache_reference.conf"

  def enabled = getBoolean("enabled", true)
  def className = getString("class", "collins.cache.GuavaCache")
  def timeoutMs = getMilliseconds("timeout").getOrElse(10000L)

  override def validateConfig() {
    if (enabled) {
      className
      timeoutMs
    }
  }
}
