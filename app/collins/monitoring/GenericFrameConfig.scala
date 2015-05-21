package collins.monitoring

import collins.util.config.ConfigValue
import collins.util.config.Configurable

object GenericFrameConfig extends Configurable {

  override val namespace = "monitoring.GenericFrame"
  override val referenceConfigFilename = "monitoring_reference.conf"

  def urlTemplate = getString("urlTemplate")(ConfigValue.Required).get

  override def validateConfig() {
    urlTemplate
  }
}
