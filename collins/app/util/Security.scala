package util

import play.api._
import models.User

trait SecuritySpecification {
  val isSecure: Boolean
  val requiredCredentials: Seq[String]
}
case class SecuritySpec(isSecure: Boolean, requiredCredentials: Seq[String])
  extends SecuritySpecification

trait AuthenticationProvider {
  def authenticate(username: String, password: String): Option[User]
}
trait AuthenticationAccessor {
  def getAuthentication(): AuthenticationProvider
}

object AuthenticationProvider {
  val Default = new MockAuthenticationProvider
  val Types = Set("ldap", "default")
  def get(name: String, config: Configuration): AuthenticationProvider = {
    name match {
      case "ldap" =>
        new LdapAuthenticationProvider(config)
      case "default" =>
        Default
    }
  }
}

class LdapAuthenticationProvider(config: Configuration) extends AuthenticationProvider {
  override def authenticate(username: String, password: String): Option[User] = {
    None
  }
}

class MockAuthenticationProvider extends AuthenticationProvider {
  case class MockUser(_username: String, _password: String, _roles: Seq[String], _id: Int, _authenticated: Boolean) {
    def toUser(): User = new User(_username, _password) {
      override def isAuthenticated() = _authenticated
      override def id() = _id
      override def roles() = _roles
    }
  }

  val users = Map(
    "blake" -> MockUser("blake", "admin:first", Seq("engineering"), 1024, false),
    "matt" -> MockUser("matt", "foobar", Seq("engineering", "management"), 1025, false),
    "test" -> MockUser("test", "fizz", Nil, 1026, false)
  )

  override def authenticate(username: String, password: String): Option[User] = {
    users.get(username) match {
      case None => None
      case Some(user) => (password == user._password) match {
        case true =>
          val newUser = user.copy(_password = "*", _authenticated = true)
          Some(newUser.toUser)
        case false => None
      }
    }
  }
}
