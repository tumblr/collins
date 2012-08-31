package util
package provisioner

import concurrent.RateLimit
import config.Configurable

object ProvisionerConfig extends Configurable {
  override val namespace = "provisioner"
  override val referenceConfigFilename = "provisioner_reference.conf"

  def enabled = getBoolean("enabled", false)
  def rate = getString("rate", "1/10 seconds")

  override protected def validateConfig() {
    if (enabled) {
      RateLimit.fromString(rate)
    }
  }
}
