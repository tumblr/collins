package util

import play.api._
import play.api.Configuration.Config
import models.User
import org.specs2.mutable._
import java.io.File

object AuthenticationProviderSpec extends Specification {
  "Authentication Providers" should {
    "work with default authentication" >> {
      val provider = AuthenticationProvider.Default
      provider.authenticate("blake", "admin:first") must beSome[User]
      provider.authenticate("no", "suchuser") must beNone
    }
    "work with LDAP authentication" >> {
      val thisFile = new File("")
      val configData = Map(
        "authentication.type" -> Config("authentication.type", "ldap", thisFile),
        "authentication.host" -> Config("authentication.host", "admin02.jfk01.tumblr.net", thisFile),
        "authentication.searchbase" -> Config("authentication.searchbase", "dc=corp,dc=tumblr,dc=net", thisFile)
      )
      val config = Configuration(configData)
      val authConfig = config.getSub("authentication")
      authConfig must beSome
      val provider = AuthenticationProvider.get("ldap", authConfig.get)
      provider must haveClass[LdapAuthenticationProvider]
      val ups = Seq(
        ("test_eng", "test_eng-franklin595%", 1093, Seq("engineering")),
        ("test_noeng", "test_noeng-franklin595%", 1094, Seq("ny")))
      ups.foreach { case(username,password,id,roles) =>
        val user = provider.authenticate(username, password)
        user must beSome[User]
        user.get.username mustEqual username
        user.get.password mustNotEqual password
        user.get.isAuthenticated must beTrue
        user.get.id mustEqual id
        user.get.roles mustEqual roles
      }
      provider.authenticate("fizz", "buzz") must beNone
    } // with LDAP authentication
  }
}
