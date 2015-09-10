package collins.cache

import collins.guava.GuavaConfig
import collins.hazelcast.HazelcastConfig
import collins.util.config.Configurable
import collins.util.config.ConfigurationException

object CacheConfig extends Configurable {

  override val namespace = "cache"
  override val referenceConfigFilename = "cache_reference.conf"

  def enabled = getBoolean("enabled", true)
  def cacheType = getString("type", "in-memory")

  override protected def validateConfig() {
    logger.debug(s"Loading domain model cache specification enabled - $enabled")
    if (enabled) {
      if (cacheType != "in-memory" && cacheType != "distributed")
        throw new ConfigurationException("Please specify cache type of 'in-memory' or 'distributed'")

      if (cacheType == "in-memory") {
        if (!GuavaConfig.enabled) {
          throw new ConfigurationException("In memory cache uses Guava, please enable and configure it.")
        }
      } else {
        if (!HazelcastConfig.enabled) {
          throw new ConfigurationException("Distributed cache uses hazelcast, please enable and configure it.")
        }

        if (HazelcastConfig.members.isEmpty()) {
          logger.warn("No cluster members found when instantiating distributed cache, treating as single node cache - use in-memory instead")
        }
      }
    }
  }
}