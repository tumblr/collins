package collins.monitoring

import collins.util.config.Configurable

object MonitoringConfig extends Configurable {
  override val namespace = "monitoring"
  override val referenceConfigFilename = "monitoring_reference.conf"

  def enabled = getBoolean("enabled", false)
  def className = getString("class", "collins.monitoring.GenericFrame")

  override def validateConfig() {
    if (enabled) {
      className
    }
  }
}
