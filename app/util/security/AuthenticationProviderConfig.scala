package util
package security

import util.config.{Configurable, ConfigValue, Registerable}

class AuthenticationProviderConfig private[security]() extends Configurable {
  override val namespace = "authentication"
  override val referenceConfigFilename = "authentication_reference.conf"

  def adminGroup = getStringSet("adminGroup")
  def cacheCredentials = getBoolean("cacheCredentials", false)
  def cacheTimeout = getMilliseconds("cacheTimeout").getOrElse(0L)
  lazy val permissionsFile = getString("permissionsFile")(ConfigValue.Required).get
  def authType = getString("type", "default").toLowerCase

  //protected def implementation(name: String) = getObject(name)

  override protected def validateConfig() {
    FileWatcher.fileGuard(permissionsFile)
  }
}
object AuthenticationProviderConfig extends Registerable(new AuthenticationProviderConfig())

class FileAuthenticationProviderConfig private[security]() extends Configurable {

  override val namespace = "authentication.file"
  override val referenceConfigFilename = "authentication_reference.conf"

  def userfile = getString("userfile")(ConfigValue.Required).get

  override protected def validateConfig() {
    if (AuthenticationProviderConfig().authType == "file") {
      FileWatcher.fileGuard(userfile)
    }
  }
}
object FileAuthenticationProviderConfig extends Registerable(new FileAuthenticationProviderConfig())
