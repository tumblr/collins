package collins.hazelcast

import java.io.File

import collins.util.config.Configurable
import collins.util.config.ConfigurationException

object HazelcastConfig extends Configurable {
  override val namespace = "hazelcast"
  override val referenceConfigFilename = "hazelcast_reference.conf"

  def enabled = getBoolean("enabled", true)
  def configFile = getString("configFile", "conf/hazelcast/hazelcast.xml")
  def members = getString("members", "")

  override protected def validateConfig() {
    logger.debug(s"Hazelcast is enabled - $enabled")
    if (enabled) {
      val f = new File(configFile)
      if (!f.exists() || !f.canRead()) {
        throw new ConfigurationException(f"Cache config file $configFile%s does not exists or is not readable")
      }
    }
  }
}