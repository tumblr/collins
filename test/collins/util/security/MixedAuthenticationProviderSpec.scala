package collins.util.security

import play.api.Configuration
import collins.models.User
import collins.util.config.ConfigurationException
import org.specs2.mutable.Specification
import collins.ResourceFinder
import play.api.test.WithApplication
import play.api.test.FakeApplication

class MixedAuthenticationProviderSpec extends Specification with ResourceFinder {
  
  "Authentication" should {
    
    "Return None if no method succeeds" in new WithApplication(FakeApplication(additionalConfiguration = Map(
      "authentication.type" -> "default"
    ))) {
      val user = User.authenticate("blake", "badpassword")
      user must beNone
    }
    
    "Find user in default when auth mode is default and file" in new WithApplication(FakeApplication(additionalConfiguration = Map(
      "authentication.type" -> "default,file",
      "authentication.permissionsFile" -> "conf/permissions.yaml",
      "authentication.file.userfile" -> findResource("htpasswd_users").getAbsolutePath
    ))) {
      // user is in default provider
      val user = User.authenticate("test", "fizz")
      (user must beSome[User]) and (user.get.username must_== "test")
    }
    
    "Find user in file when auth mode is default and file" in new WithApplication(FakeApplication(additionalConfiguration = Map(
      "authentication.type" -> "default,file",
      "authentication.permissionsFile" -> "conf/permissions.yaml",
      "authentication.file.userfile" -> findResource("htpasswd_users").getAbsolutePath
    ))) {
      // user is in file
      val user = User.authenticate("testuser2", "testpassword")
      (user must beSome[User]) and (user.get.username must_== "testuser2")
    }
  }
}
