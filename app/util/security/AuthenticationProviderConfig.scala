package util
package security

import util.config.{Configurable, ConfigValue}
import collins.validation.File

object AuthenticationProviderConfig extends Configurable {
  override val namespace = "authentication"
  override val referenceConfigFilename = "authentication_reference.conf"

  def adminGroup = getStringSet("adminGroup").map(_.toLowerCase)
  def cacheCredentials = getBoolean("cacheCredentials", false)
  def cacheTimeout = getMilliseconds("cacheTimeout").getOrElse(0L)
  def cachePermissionsTimeout = getMilliseconds("cachePermissionsTimeout").getOrElse(30000L)
  def permissionsFile = getString("permissionsFile")(ConfigValue.Required).get
  def authType = getString("type", "default").toLowerCase

  override protected def validateConfig() {
    File.requireFileIsReadable(permissionsFile)
    require(cachePermissionsTimeout > 0, "cachePermissionsTimeout must be > 0")
    PermissionsLoader()
  }
}

object FileAuthenticationProviderConfig extends Configurable {

  override val namespace = "authentication.file"
  override val referenceConfigFilename = "authentication_reference.conf"

  def userfile = getString("userfile")(ConfigValue.Required).get

  override protected def validateConfig() {
    if (AuthenticationProviderConfig.authType == "file") {
      logger.debug("User authentication file " + userfile)
      File.requireFileIsReadable(userfile)
    }
  }
}

object LdapAuthenticationProviderConfig extends Configurable {

  override val namespace = "authentication.ldap"
  override val referenceConfigFilename = "authentication_reference.conf"

  val RFC_2307 = "rfc2307"
  val RFC_2307_BIS = "rfc2307bis"
  val ValidSchemas = Set(RFC_2307, RFC_2307_BIS)

  def groupsub = getString("groupsub")(ConfigValue.Required).get
  def groupAttribute = getString("groupAttribute")(ConfigValue.Required).get
  def host = getString("host")(ConfigValue.Required).get
  def userAttribute = getString("userAttribute", "uid")
  def schema = getString("schema")(ConfigValue.Required).map(_.toLowerCase).get
  def searchbase = getString("searchbase")(ConfigValue.Required).get
  def usersub = getString("usersub")(ConfigValue.Required).get
  def useSsl = getBoolean("ssl").getOrElse(false)

  def isRfc2307 = schema == RFC_2307
  def isRfc2307Bis = schema == RFC_2307_BIS

  override protected def validateConfig() {
    if (AuthenticationProviderConfig.authType == "ldap") {
      host
      if (!ValidSchemas.contains(schema)) {
        throw globalError("%s is not one of %s".format(
          schema, ValidSchemas.mkString(",")
        ))
      }
      searchbase
      usersub
      groupsub
      groupAttribute
    }
  }
}
