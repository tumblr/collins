package util
package security

import models.{User, UserImpl}

import sun.misc.BASE64Encoder
import com.google.common.cache.{CacheBuilder, LoadingCache}
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import io.Source

class FileAuthenticationProvider() extends AuthenticationProvider {

  def userfile = FileAuthenticationProviderConfig.userfile
  override val authType = "file"

  lazy private val userCache: LoadingCache[String,FileUserMap] = CacheBuilder.newBuilder()
                                      .maximumSize(1)
                                      .expireAfterWrite(10, TimeUnit.SECONDS)
                                      .build(FileUserLoader())

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
