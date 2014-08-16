package collins.monitoring

import util.config.Configurable
import collins.cache.ConfigCache

object MonitoringConfig extends Configurable {
  override val namespace = "monitoring"
  override val referenceConfigFilename = "monitoring_reference.conf"

  def enabled = getBoolean("enabled", false)
  def className = getString("class", "collins.monitoring.Icinga2")

  override def validateConfig() {
    if (enabled) {
      className
    }
  }
}
