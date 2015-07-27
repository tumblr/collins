package collins.cache

import com.google.common.cache.CacheBuilderSpec

import collins.util.config.Configurable

object CacheConfig extends Configurable {

  override val namespace = "cache"
  override val referenceConfigFilename = "cache_reference.conf"

  def enabled = getBoolean("enabled", true)
  def specification = getString("specification", "maximumSize=10000,expireAfterWrite=10s,recordStats")

  override protected def validateConfig() {
    if (enabled) {
      logger.debug("Validating domain model cache specification")
      CacheBuilderSpec.parse(specification)
    }
  }
}