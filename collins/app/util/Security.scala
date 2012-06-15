package util

import models.{User, UserImpl}

import play.api._
import com.tumblr.play.{PermissionsHelper, Privileges}
import java.io.File
import annotation.implicitNotFound

@implicitNotFound(msg = "Didn't find an implicit SecuritySpecification but expected one")
trait SecuritySpecification {
  val isSecure: Boolean
  val requiredCredentials: Set[String]
  val securityConcern: String

  def requiresAuthorization: Boolean = requiredCredentials.nonEmpty
}

case class SecuritySpec(
  isSecure: Boolean,
  requiredCredentials: Set[String],
  securityConcern: String = SecuritySpec.LegacyMarker
) extends SecuritySpecification {
  def this(secure: Boolean, creds: Seq[String]) = this(secure, creds.toSet, SecuritySpec.LegacyMarker)
}

object SecuritySpec {
  val LegacyMarker = "SecuritySpec Version 1.1"
  def apply(isSecure: Boolean, requiredCredentials: String) =
    new SecuritySpec(isSecure, Set(requiredCredentials))
  def apply(creds: Set[String]) = new SecuritySpec(true, creds)
  def apply(secure: Boolean, creds: Seq[String]) =
    new SecuritySpec(secure, creds.toSet)
  def apply(isSecure: Boolean) = new SecuritySpec(isSecure, Set[String]())
  def fromConfig(concern: String, default: SecuritySpecification): SecuritySpecification = {
    AuthenticationProvider.permissions(concern) match {
      case None =>
        SecuritySpec(default.isSecure, default.requiredCredentials, default.securityConcern)
      case Some(set) =>
        new SecuritySpec(true, set, concern)
    }
  }
}

trait AuthenticationProvider {
  protected val logger = Logger.logger
  def authenticate(username: String, password: String): Option[User]
  def validate() {
  }
}
trait AuthenticationAccessor {
  def getAuthentication(): AuthenticationProvider
}

object AuthenticationProvider {
  val Default = new MockAuthenticationProvider
  val Types = Set("ldap", "file", "default", "ipa")
  val filename = Config.getString("authentication", "permissionsFile", "conf/permissions.yaml").toString

  private val logger = Logger(getClass)

  lazy private val watcher = FileWatcher.watchWithResults(filename, Privileges.empty) { f =>
    PermissionsHelper.fromFile(f.getAbsolutePath)
  }

  def validate() {
    FileWatcher.fileGuard(filename)
  }

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

  def permissions(concern: String): Option[Set[String]] = {
    val p = privileges
    if (p.hasConcern(concern)) {
      val c = p.getConcern(concern)
      logger.debug("Concern '%s' has concerns '%s'".format(
        concern, c.mkString(",")))
      Some(c)
    } else {
      logger.debug("Missing configuration for concern %s".format(concern))
      None
    }
  }

  def userIsAuthorized(user: User, spec: SecuritySpecification): Boolean = {
    val p = privileges
    val concern = spec.securityConcern
    if (concern == SecuritySpec.LegacyMarker) {
      logger.debug("Found legacy security spec, defaulting to basic roles")
      loggedAuth {
        user.roles.intersect(spec.requiredCredentials).size > 0
      }
    } else {
      logger.debug("Have concern '%s'".format(concern))
      loggedAuth {
        user.roles
          .find { role =>
            val perm = p.groupHasPermission(role, concern)
            logger.debug("Checking group permission for role %s concern %s was %s".format(
              role, concern, perm.toString))
            perm
          }
          .map(_ => true)
          .getOrElse {
            val perm = p.userHasPermission(user.username, concern)
            logger.debug("Checking user permission for username %s concern %s was %s".format(
              user.username, concern, perm.toString))
            perm
          }
      }
    }
  }

  private def loggedAuth(f: => Boolean): Boolean = {
    val r = f
    logger.debug("Result of authentication was %s".format(r.toString))
    r
  }

  private def privileges: Privileges = {
    val p = watcher.getFileContents()
    logger.debug("Privileges - %s".format(p))
    p
  }

}
