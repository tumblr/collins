package util

import models.{User, UserImpl}

import play.api._

import annotation.implicitNotFound

@implicitNotFound(msg = "Didn't find an implicit SecuritySpecification but expected one")
trait SecuritySpecification {
  val isSecure: Boolean
  val requiredCredentials: Seq[String]
}
case class SecuritySpec(isSecure: Boolean, requiredCredentials: Seq[String]) extends SecuritySpecification
object SecuritySpec {
  def apply(isSecure: Boolean, requiredCredentials: String) =
    new SecuritySpec(isSecure, Seq(requiredCredentials))
  def apply(isSecure: Boolean) = new SecuritySpec(isSecure, Nil)
}

trait AuthenticationProvider {
  protected val logger = Logger.logger
  def authenticate(username: String, password: String): Option[User]
}
trait AuthenticationAccessor {
  def getAuthentication(): AuthenticationProvider
}

object AuthenticationProvider {
  val Default = new MockAuthenticationProvider
  val Types = Set("ldap", "file", "default", "ipa")
  def get(name: String, config: Configuration): AuthenticationProvider = {
    name match {
      case "default" =>
        Default
      case "file" =>
        new FileAuthenticationProvider(config)
      case "ipa" =>
        new IpaAuthenticationProvider(config)
      case "ldap" =>
        new LdapAuthenticationProvider(config)
    }
  }
}
