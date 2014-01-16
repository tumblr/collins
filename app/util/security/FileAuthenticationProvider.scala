package util
package security

import collins.cache.ConfigCache
import models.{User, UserImpl}

import sun.misc.BASE64Encoder
import java.io.File
import java.security.MessageDigest
import io.Source

class FileAuthenticationProvider() extends AuthenticationProvider {

  def userfile = FileAuthenticationProviderConfig.userfile
  override val authType = "file"

  lazy private val userCache = ConfigCache.create(10000L, FileUserLoader())

  override def authenticate(username: String, password: String): Option[User] = {
    user(username) match {
      case None => None
      case Some(u) => (hash(password) == u.password) match {
        case true =>
          val newUser = u.copy(_password = "*", _authenticated = true)
          Some(newUser)
        case false => None
      }
    }
  }

  protected def user(username: String): Option[UserImpl] = {
    userCache.get(userfile).get(username)
  }

  // This is consistent with how apache encrypts SHA1
  protected def hash(s: String): String = {
    "{SHA}" + new BASE64Encoder().encode(MessageDigest.getInstance("SHA1").digest(s.getBytes()))
  }
}

