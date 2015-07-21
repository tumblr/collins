package collins.util.security

import play.api.Logger

import collins.models.User
import collins.permissions.Privileges
import collins.cache.GuavaCacheFactory

trait AuthenticationProvider {
  protected val logger = Logger.logger
  def authType: Array[String]
  def authenticate(username: String, password: String): Option[User]
}

object AuthenticationProvider {
  private val logger = Logger("collins.util.security.AuthenticationProvider")

  lazy private val permissionsCache =   
    GuavaCacheFactory.create(AuthenticationProviderConfig.permissionsCacheSpecification, PermissionsLoader())

  def get(types: Array[String]): AuthenticationProvider = {
    new MixedAuthenticationProvider(types)
  }

  def permissions(concern: String): Option[Set[String]] = {
    val p = privileges
    if (p.hasConcern(concern)) {
      val c = p.getConcern(concern)
      logger.trace("Concern '%s' has concerns '%s'".format(
        concern, c.mkString(",")))
      Some(c)
    } else {
      logger.trace("Missing configuration for concern %s".format(concern))
      None
    }
  }

  def userIsAuthorized(user: User, spec: SecuritySpecification): Boolean = {
    val p = privileges
    val concern = spec.securityConcern
    if (concern == SecuritySpec.LegacyMarker) {
      logger.debug("Found legacy security spec, defaulting to basic roles")
      loggedAuth {
        user.roles.map(_.toLowerCase).intersect(spec.requiredCredentials.map(_.toLowerCase)).size > 0
      }
    } else {
      logger.trace("Have concern '%s'".format(concern))
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

  protected[util] def privileges: Privileges = {
    val p = permissionsCache.get(AuthenticationProviderConfig.permissionsFile)
    logger.trace("Privileges - %s".format(p))
    p
  }

}
