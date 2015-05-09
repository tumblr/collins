package collins.graphs

import collins.util.config.ConfigAccessor
import collins.util.config.ConfigSource
import collins.util.config.ConfigValue
import collins.util.config.TypesafeConfiguration

case class CustomMetricConfig(
  override val source: TypesafeConfiguration
) extends ConfigAccessor with ConfigSource {
  def selector = getString("selector")(ConfigValue.Required).get
  def metrics = getStringSet("metrics")
  def validateConfig() {
    selector
    metrics
  }
}
