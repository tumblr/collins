package collins.graphs

import util.config.{ConfigAccessor, ConfigSource, ConfigValue, TypesafeConfiguration}

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
