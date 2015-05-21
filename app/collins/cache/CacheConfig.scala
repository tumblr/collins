package collins.cache

import collins.util.config.Configurable

object CacheConfig extends Configurable {
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
