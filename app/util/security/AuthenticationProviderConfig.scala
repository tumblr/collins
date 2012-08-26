package util
package security

import util.config.{Configurable, ConfigValue}

object AuthenticationProviderConfig extends Configurable {
  val namespace = "authentication"
  val referenceConfigFilename = "authentication_reference.conf"

  def adminGroup = getStringSet("adminGroup")
  def permissionsFile = getString("permissionsFile")(ConfigValue.Required).get

  override protected def validateConfig() {
    FileWatcher.fileGuard(permissionsFile)
  }
}
