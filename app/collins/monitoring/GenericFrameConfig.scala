package collins.monitoring

import util.config.{Configurable, ConfigValue}

object GenericFrameConfig extends Configurable {

  override val namespace = "monitoring.GenericFrame"
  override val referenceConfigFilename = "monitoring_reference.conf"

  def urlTemplate = getString("urlTemplate")(ConfigValue.Required).get

  override def validateConfig() {
    urlTemplate
  }
}
