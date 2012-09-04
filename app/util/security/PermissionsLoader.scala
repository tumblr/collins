package util.security

import collins.permissions.{PermissionsHelper, Privileges}
import collins.validation.File

import play.api.Logger
import com.google.common.cache.CacheLoader
import java.util.concurrent.TimeUnit
import java.io.{File => IoFile}

case class PermissionsLoader(privileges: Privileges) extends CacheLoader[String, Privileges] {

  private[this] val logger = Logger("util.security.PermissionsLoader")

  override def load(filename: String): Privileges = {
    logger.debug("Refreshing permissions from %s".format(filename))
    try {
      File.requireFileIsReadable(filename)
      PermissionsHelper.fromFile(new IoFile(filename).getAbsolutePath)
    } catch {
      case e =>
        logger.error("There is a problem with permissions file %s: %s".format(
          filename, e.getMessage
        ))
        privileges
    }
  }
}
object PermissionsLoader {
  def apply(): PermissionsLoader = {
    val permissionsFile = AuthenticationProviderConfig.permissionsFile
    val privileges = PermissionsHelper.fromFile(new IoFile(permissionsFile).getAbsolutePath)
    PermissionsLoader(privileges)
  }
}
