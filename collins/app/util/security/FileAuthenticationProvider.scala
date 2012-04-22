package util

import models.{User, UserImpl}

import play.api.Configuration

import java.util.concurrent.atomic.AtomicReference
import java.util.Date
import sun.misc.BASE64Encoder
import java.io.File
import java.security.MessageDigest
import io.Source

class FileAuthenticationProvider(config: Configuration) extends AuthenticationProvider {
  @volatile private var lastModificationTime = 0L
  @volatile private var lastCheckTime = 0L
  private val usersMap = new AtomicReference[Map[String, UserImpl]](Map.empty)
  private val maximumTime = 30L

  config.getString("type").foreach { cfg =>
    require(cfg.toLowerCase == "file", "If specified, authentication type must be file")
  }

  private val filename = config.getString("file").getOrElse {
    throw new IllegalArgumentException("authentication.file not specified")
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
    if (!useCache) {
      refreshCache
    }
    usersMap.get().get(username)
  }

  protected def useCache: Boolean = {
    lastCheckTime > (now - maximumTime)
  }

  protected def refreshCache() {
    val file = getFile
    val mTime = epoch(file.lastModified)
    if (lastModificationTime >= mTime) {
      return
    }
    lastModificationTime = mTime
    usersMap.set(usersFromFile())
  }

  protected def usersFromFile(): Map[String,UserImpl] = {
    Source.fromFile(getFile, "UTF-8").getLines().map { line =>
      val split = line.split(":", 3)
      if (split.length != 3) {
        throw new Exception("Invalid line format for users")
      }
      val username = split(0)
      val password = split(1)
      val roles = split(2).split(",").toSeq
      (username -> UserImpl(username, password, roles, username.hashCode, false))
    }.toMap
  }

  protected def getFile: File = {
    val file = new File(filename)
    require(file.exists() && file.canRead(), "File %s does not exist".format(filename))
    file
  }

  protected def epoch(seed: Long = 0): Long = if (seed <= 0) {
    (new Date().getTime()/1000L)
  } else {
    seed/1000L
  }
  protected def now: Long = epoch(0)


  // This is consistent with how apache encrypts SHA1
  protected def hash(s: String): String = {
    "{SHA}" + new BASE64Encoder().encode(MessageDigest.getInstance("SHA1").digest(s.getBytes()))
  }
}

