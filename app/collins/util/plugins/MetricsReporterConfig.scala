package collins.util.plugins

import collins.util.config.ConfigValue
import collins.util.config.Configurable
import collins.validation.File

object MetricsReporterConfig extends Configurable {

  override val namespace = "metricsreporter"
  override val referenceConfigFilename = "metricsreporter_reference.conf"

  def enabled = getBoolean("enabled", false)
  def configFile = getString("configFile")(ConfigValue.Required).get

  override def validateConfig() = if (enabled) {
    File.requireFileIsReadable(configFile)
  }

}
