package util
package security

import play.api._
import play.api.Configuration
import models.User
import org.specs2.mutable._
import java.io.File
import play.api.test.WithApplication
import play.api.test.FakeApplication

object AuthenticationProviderSpec extends Specification with _root_.test.ResourceFinder {

  "Authentication Providers" should {
    "work with default authentication" >> {
      val provider = AuthenticationProvider.Default
      provider.authenticate("blake", "admin:first") must beSome[User]
      provider.authenticate("no", "suchuser") must beNone
    }
    val authFile = findResource("htpasswd_users")
    "work with file based auth" in new WithApplication(FakeApplication(additionalConfiguration=Map(
        "authentication.file.userfile" -> authFile.getAbsolutePath
      ))) {
      val provider = AuthenticationProvider.get("file")

      val users = Seq(
        ("blake", "password123", Set("engineering")),
        ("testuser", "FizzBuzzAbc", Set("ny","also"))
      )

      users.foreach { case(username,password,roles) =>
        val user = provider.authenticate(username, password)
        user must beSome[User]
        user.get.username mustEqual username
        user.get.password mustNotEqual password
        user.get.isAuthenticated must beTrue
        user.get.roles mustEqual roles
      }
      provider.authenticate("blake", "abbazabba") must beNone
    }
  }
}
