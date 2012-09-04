package util
package security

import util.config.{Configurable, ConfigValue}
import collins.permissions.PermissionsHelper
import collins.validation.File
import java.io.{File => IoFile}

object AuthenticationProviderConfig extends Configurable {
  override val namespace = "authentication"
  override val referenceConfigFilename = "authentication_reference.conf"

  def adminGroup = getStringSet("adminGroup").map(_.toLowerCase)
  def cacheCredentials = getBoolean("cacheCredentials", false)
  def cacheTimeout = getMilliseconds("cacheTimeout").getOrElse(0L)
  def permissionsFile = getString("permissionsFile")(ConfigValue.Required).get
  def authType = getString("type", "default").toLowerCase

  override protected def validateConfig() {
    File.requireFileIsReadable(permissionsFile)
    PermissionsLoader()
  }
}

object FileAuthenticationProviderConfig extends Configurable {

  override val namespace = "authentication.file"
  override val referenceConfigFilename = "authentication_reference.conf"

  def userfile = getString("userfile")(ConfigValue.Required).get

  override protected def validateConfig() {
    if (AuthenticationProviderConfig.authType == "file") {
      File.requireFileIsReadable(userfile)
    }
  }
}

object LdapAuthenticationProviderConfig extends Configurable {

  override val namespace = "authentication.ldap"
  override val referenceConfigFilename = "authentication_reference.conf"

  def host = getString("host")(ConfigValue.Required).get
  def searchbase = getString("searchbase")(ConfigValue.Required).get
  def usersub = getString("usersub")(ConfigValue.Required).get
  def groupsub = getString("groupsub")(ConfigValue.Required).get
  def groupAttribute = getString("groupAttribute")(ConfigValue.Required).get
  def useSsl = getBoolean("ssl").getOrElse(false)

  override protected def validateConfig() {
    if (AuthenticationProviderConfig.authType == "ldap") {
      host
      searchbase
      usersub
      groupsub
      groupAttribute
    }
  }
}
