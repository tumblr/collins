package util

import models.{User, UserImpl}

class MockAuthenticationProvider extends AuthenticationProvider {
  val users = Map(
    "blake" -> UserImpl("blake", "admin:first", Seq("engineering","infra","ops"), 1024, false),
    "matt" -> UserImpl("matt", "foobar", Seq("engineering", "management"), 1025, false),
    "test" -> UserImpl("test", "fizz", Nil, 1026, false)
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
