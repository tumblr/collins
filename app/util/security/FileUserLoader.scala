package util.security

import com.tumblr.play.interop.PrivilegedHelper

import collins.validation.File
import models.{User, UserImpl}

import play.api.Logger
import com.google.common.cache.CacheLoader
import io.Source
import java.io.{File => IoFile}

case class FileUserLoader(users: FileUserMap) extends CacheLoader[String,FileUserMap] {

  private[this] val logger = Logger("util.security.FileUserLoader")

  override def load(filename: String): FileUserMap = {
    logger.debug("Refreshing users list from %s".format(filename))
    try {
      File.requireFileIsReadable(filename)
      FileUserLoader.fromFile(new IoFile(filename))
    } catch {
      case e =>
        logger.error("There is a problem with the users file %s: %s".format(
          filename, e.getMessage
        ))
        users
    }
  }
}
object FileUserLoader {
  def apply(): FileUserLoader = {
    val usersFile = FileAuthenticationProviderConfig.userfile
    val users = fromFile(new IoFile(usersFile))
    FileUserLoader(users)
  }
  def fromFile(file: IoFile): FileUserMap = FileUserMap{
    val src = Source.fromFile(file, "UTF-8")
    val lines = src.getLines
    src.close()
    lines.map { line =>
      val split = line.split(":", 3)
      if (split.length != 3) {
        throw FileUserLoaderException("Invalid line format for users")
      }
      val username = split(0)
      val password = split(1)
      val roles = split(2).split(",").toSet
      (username -> UserImpl(username, password, roles, username.hashCode, false))
    }.toMap
  }
}
