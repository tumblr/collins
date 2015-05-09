package collins.util.security

import play.api.Configuration
import collins.models.User
import collins.util.config.ConfigurationException
import org.specs2.mutable.Specification
import collins.ResourceFinder
import play.api.test.WithApplication

class MixedAuthenticationProviderSpec extends Specification with ResourceFinder {
  
  "Authentication" should {
    "Authenticate users in htaccess type files" in new WithApplication {
      authUser()
    }
    
    "Stop iterating if a user is found" in new WithApplication {
      shortCircuit()
    }
    
    "Return None if no method succeeds" in new WithApplication {
      authBadUser()
    }
    
    "Attempt each method in order" in new WithApplication {
      allTypes()
    }
    
    "Fail when bad type specified" in new WithApplication {
      enforceKnownTypes()
    }
  }

  def configure() {
    val authFile = findResource("htpasswd_users")
    val configData = Map(
      "authentication.type" -> "file",
      "authentication.permissionsFile" -> "conf/permissions.yaml",
      "userfile" -> authFile.getAbsolutePath
    )

    val config = Configuration.from(configData)
    collins.util.config.AppConfig.globalConfig = Some(config)
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

  def enforceKnownTypes() = {
    val prov = new MixedAuthenticationProvider("unknown")
    prov.authenticate("", "") must throwAn[AuthenticationException](message = "Invalid authentication type provided: unknown")
  }
}
