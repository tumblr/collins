package collins.monitoring

import util.config.{Configurable, ConfigValue}

object Icinga2Config extends Configurable {

  override val namespace = "monitoring.Icinga2"
  override val referenceConfigFilename = "monitoring_reference.conf"

  def url = getString("url")(ConfigValue.Required).get

  override def validateConfig() {
    url
  }
}
