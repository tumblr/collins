package collins.softlayer

import models.Status
import util.config.Configurable

object SoftLayerConfig extends Configurable {
  override val namespace = "softlayer"
  override val referenceConfigFilename = "softlayer_reference.conf"

  def enabled = getBoolean("enabled", false)
  def username = getString("username", "")
  def password = getString("password", "")
  def allowedCancelStatus = getStringSet("allowedCancelStatus", Status.statusNames).map { s =>
    Status.findByName(s).get.id
  }
  def cancelRequestTimeoutMs = getMilliseconds("cancelRequestTimeout").getOrElse(10000L)
  def activationRequestTimeoutMs = getMilliseconds("activationRequestTimeout").getOrElse(10000L)

  override def validateConfig() {
    if (enabled) {
      require(username.nonEmpty, "softlayer.username must not be empty if enabled")
      require(password.nonEmpty, "softlayer.password must not be empty if enabled")
      allowedCancelStatus
    }
  }
}


