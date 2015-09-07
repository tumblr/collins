package collins.util.security

import com.google.common.cache.CacheBuilderSpec

import collins.util.config.ConfigValue
import collins.util.config.Configurable
import collins.validation.File

object AuthenticationProviderConfig extends Configurable {
  override val namespace = "authentication"
  override val referenceConfigFilename = "authentication_reference.conf"

  def adminGroup = getStringSet("adminGroup").map(_.toLowerCase)
  def permissionsCacheSpecification = getString("permissionsCacheSpecification", "expireAfterWrite=30s")
  def permissionsFile = getString("permissionsFile")(ConfigValue.Required).get
  def authType = getString("type", "default").split(",").toList.map(_.trim.toLowerCase)

  override protected def validateConfig() {
    File.requireFileIsReadable(permissionsFile)
    CacheBuilderSpec.parse(permissionsCacheSpecification)
    PermissionsLoader()
  }
}

object FileAuthenticationProviderConfig extends Configurable {

  override val namespace = "authentication.file"
  override val referenceConfigFilename = "authentication_reference.conf"

  def userfile = getString("userfile")(ConfigValue.Required).get
  def cacheSpecification = getString("cacheSpecification", "expireAfterWrite=30s")

  override protected def validateConfig() {
    if (AuthenticationProviderConfig.authType.contains("file")) {
      logger.debug("User authentication file " + userfile)
      CacheBuilderSpec.parse(cacheSpecification)
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

  def cacheSpecification = getString("cacheSpecification", "expireAfterWrite=30s")
  def groupsub = getString("groupsub")(ConfigValue.Required).get
  def groupAttribute = getString("groupAttribute")(ConfigValue.Required).get
  def host = getString("host")(ConfigValue.Required).get
  def schema = getString("schema")(ConfigValue.Required).map(_.toLowerCase).get
  def searchbase = getString("searchbase")(ConfigValue.Required).get
  def usersub = getString("usersub")(ConfigValue.Required).get
  def useSsl = getBoolean("ssl").getOrElse(false)
  def anonymous = getBoolean("anonymous").getOrElse(false)
  def binddn = getString("binddn").getOrElse("")
  def bindpwd = getString("bindpwd").getOrElse("")
  def userAttribute = getString("userAttribute").getOrElse("uid")
  def userNumberAttribute = getString("userNumberAttribute").getOrElse("uidNumber")
  def groupNameAttribute = getString("groupNameAttribute").getOrElse("cn")

  def isRfc2307 = schema == RFC_2307
  def isRfc2307Bis = schema == RFC_2307_BIS

  override protected def validateConfig() {
    if (AuthenticationProviderConfig.authType.contains("ldap")) {
      CacheBuilderSpec.parse(cacheSpecification)
      host
      if (!ValidSchemas.contains(schema)) {
        throw globalError("%s is not one of %s".format(
          schema, ValidSchemas.mkString(",")))
      }
      searchbase
      usersub
      groupsub
      groupAttribute
      searchbase
    }
  }
}
