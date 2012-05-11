package util

import models.{User, UserImpl}

import play.api.Configuration

import sun.misc.BASE64Encoder
import java.io.File
import java.security.MessageDigest
import io.Source

class FileAuthenticationProvider(config: Configuration) extends AuthenticationProvider {
  config.getString("type").foreach { cfg =>
    require(cfg.toLowerCase == "file", "If specified, authentication type must be file")
  }

  private val authfile = config.getString("file").getOrElse {
    throw new IllegalArgumentException("authentication.file not specified")
  }

  private val watcher = FileWatcher.watchWithResults(authfile, Map.empty[String,UserImpl]) { file =>
    usersFromFile(file)
  }

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
    watcher.getFileContents().get(username)
  }

  protected def usersFromFile(file: File): Map[String,UserImpl] = {
    Source.fromFile(file, "UTF-8").getLines().map { line =>
      val split = line.split(":", 3)
      if (split.length != 3) {
        throw new Exception("Invalid line format for users")
      }
      val username = split(0)
      val password = split(1)
      val roles = split(2).split(",").toSet
      (username -> UserImpl(username, password, roles, username.hashCode, false))
    }.toMap
  }

  // This is consistent with how apache encrypts SHA1
  protected def hash(s: String): String = {
    "{SHA}" + new BASE64Encoder().encode(MessageDigest.getInstance("SHA1").digest(s.getBytes()))
  }
}

