package collins.util.security

import collins.models.User
import collins.models.UserImpl

class MockAuthenticationProvider extends AuthenticationProvider {
  override val authType = List("default")

  val users = Map(
    "blake" -> UserImpl("blake", "admin:first", Set("engineering","Infra","ops"), 1024, false),
    "matt" -> UserImpl("matt", "foobar", Set("engineering", "management"), 1025, false),
    "test" -> UserImpl("test", "fizz", Set[String](), 1026, false),
    "joeengineer" -> UserImpl("joeengineer", "flah", Set("engineering"), 1027, false)
  )

  override def authenticate(username: String, password: String): Option[User] = {
    users.get(username) match {
      case None => None
      case Some(user) => (password == user.password) match {
        case true =>
          val newUser = user.copy(_password = "*", _authenticated = true)
          Some(newUser)
        case false => None
      }
    }
  }
}
