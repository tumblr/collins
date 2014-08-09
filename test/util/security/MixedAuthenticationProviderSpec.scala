package util.security

import org.specs2.Specification
import org.specs2.specification.Fragments
import play.api.Configuration
import models.User
import util.config.ConfigurationException

/**
 * Please provide a concise description
 */
class MixedAuthenticationProviderSpec extends Specification with _root_.test.ResourceFinder {
  def is: Fragments =
    "The " + classOf[MixedAuthenticationProviderSpec] + " should" ^
      "Authenticate users in htaccess type files" ! authUser() ^
      "Stop iterating if a user is found"         ! shortCircuit() ^
      "Return None if no method succeeds"         ! authBadUser() ^
      "Attempt each method in order"              ! allTypes() ^
      "Fail because ldap is not stubbed out"      ! attemptLdap() ^
      "Fail when bad type specified"              ! enforceKnownTypes() ^
    end

  def configure() {
    val authFile = findResource("htpasswd_users")
    val configData = Map(
      "authentication.type" -> "file",
      "authentication.permissionsFile" -> "conf/permissions.yaml",
      "userfile" -> authFile.getAbsolutePath
    )

    val config = Configuration.from(configData)
    _root_.util.config.AppConfig.globalConfig = Some(config)
    FileAuthenticationProviderConfig.initialize()
  }

  def authUser() = {
    configure()

    val prov = new MixedAuthenticationProvider("file")
    val user = prov.authenticate("blake", "password123")

    (user must beSome[User]) and (user.get.username must_== "blake")
  }

  def shortCircuit() = {
    // If ldap is attempted, this will fail because there is no configuration registered
    // ...thus this test passes by virtue of such an exception not being raised
    val prov = new MixedAuthenticationProvider("file, ldap")
    val user = prov.authenticate("blake", "password123")

    (user must beSome[User]) and (user.get.username must_== "blake")
  }

  def authBadUser() = {
    val prov = new MixedAuthenticationProvider("file")
    val user = prov.authenticate("blake", "badpassword")

    user must beNone
  }

  def allTypes() = {
    val prov = new MixedAuthenticationProvider("file, default, ldap")
    val user = prov.authenticate("blake", "admin:first")

    (user must beSome[User]) and (user.get.username must_== "blake")
  }

  def attemptLdap() = {
    // The first two will fail, and then it will attemp ldap and fail
    val prov = new MixedAuthenticationProvider("file, default, ldap")
    prov.authenticate("ldapuser", "doesntmatter") must throwA[ConfigurationException](message = "Required configuration authentication.ldap.host not found")
  }

  def enforceKnownTypes() = {
    val prov = new MixedAuthenticationProvider("unknown")
    prov.authenticate("", "") must throwAn[AuthenticationException](message = "Invalid authentication type provided: unknown")
  }
}
