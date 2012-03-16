package util

import play.api._
import play.api.Configuration
import models.User
import org.specs2.mutable._
import java.io.File

object AuthenticationProviderSpec extends Specification with _root_.test.ResourceFinder {
  "Authentication Providers" should {
    "work with default authentication" >> {
      val provider = AuthenticationProvider.Default
      provider.authenticate("blake", "admin:first") must beSome[User]
      provider.authenticate("no", "suchuser") must beNone
    }
    "work with file based auth" >> {
      val thisFile = new File("")
      val authFile = findResource("htpasswd_users")
      val configData = Map(
        "authentication.type" -> "file",
        "authentication.file" -> authFile.getAbsolutePath
      )
      val config = Configuration.from(configData)
      val authConfig = config.getConfig("authentication")
      authConfig must beSome
      val provider = AuthenticationProvider.get("file", authConfig.get)
      provider must haveClass[FileAuthenticationProvider]
      val users = Seq(
        ("blake", "password123", Seq("engineering")),
        ("testuser", "FizzBuzzAbc", Seq("ny","also"))
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
    /*
    "work with IPA authentication" >> {
      val thisFile = new File("")
      val configData = Map(
        "authentication.type" -> "ipa",
        "authentication.host" -> "192.168.130.53",
        "authentication.searchbase" -> "cn=accounts,dc=example,dc=com",
        "authentication.usersub" -> "cn=users",
        "authentication.groupsub" -> "cn=groups"
      )
      val config = Configuration.from(configData)
      val authConfig = config.getConfig("authentication")
      authConfig must beSome
      val provider = AuthenticationProvider.get("ipa", authConfig.get)
      provider must haveClass[IpaAuthenticationProvider]
      val ups = Seq(
        ("test_eng", "test_eng-franklin595%", 654800006, Seq("infra","ipausers")),
        ("test_noeng", "test_noeng-franklin595%", 654800007, Seq("ipausers")))
      ups.foreach { case(username,password,id,roles) =>
        val user = provider.authenticate(username, password)
        user must beSome[User]
        user.get.username mustEqual username
        user.get.password mustNotEqual password
        user.get.isAuthenticated must beTrue
        user.get.id mustEqual id
        user.get.roles must containAllOf(roles)
      }
      provider.authenticate("fizz", "buzz") must beNone
    } // with IPA authentication
    "work with LDAP authentication" >> {
      val thisFile = new File("")
      val configData = Map(
        "authentication.type" -> "ldap",
        "authentication.host" -> "192.168.130.7",
        "authentication.searchbase" -> "dc=corp,dc=tumblr,dc=net",
        "authentication.usersub" -> "cn=users",
        "authentication.groupsub" -> "cn=groups"
      )
      val config = Configuration.from(configData)
      val authConfig = config.getConfig("authentication")
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
        user.get.roles must containAllOf(roles)
      }
      provider.authenticate("fizz", "buzz") must beNone
    } // with LDAP authentication
    */
  }
}
