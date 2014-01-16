package util.security

import models.User


/**
 * Exception encountered during authentication phase
 */
class AuthenticationException(msg: String) extends Exception(msg)

/**
 * Provides authentication by a variety of methods
 */
class MixedAuthenticationProvider(types: String) extends AuthenticationProvider {

  /**
   * @return The authorization type provided by this class
   */
  def authType: String = types

  /**
   * Ordered list of permitted authentication types
   */
  private val configuredTypes = types.split(",").toSeq.map(_.trim)

  /**
   * Attempt to authenticate user by each method specified in :types
   * @return Authenticated user or none
   */
  def authenticate(username: String, password: String): Option[User] = {
    logger.debug("Beginning to try authentication types")

    // Iterate over the types lazily, such that if one method passes, iteration stops
    configuredTypes.view.flatMap({
      case "default" => {
        logger.trace("mock authentication type")
        val defaultProvider = AuthenticationProvider.Default
        val user = defaultProvider.authenticate(username, password)
        logger.debug("Tried mock authentication for %s, got back %s".format(username, user))
        user
      }
      case "file" => {
        logger.trace("file authentication type")
        val fileProvider = new FileAuthenticationProvider()
        val user = fileProvider.authenticate(username, password)
        logger.debug("Tried file authentication for %s, got back %s".format(username, user))
        user
      }
      case "ldap" => {
        val ldapProvider = new LdapAuthenticationProvider()
        val user = ldapProvider.authenticate(username, password)
        logger.debug("Tried ldap authentication for %s, got back %s".format(username, user))
        user
      }
      case t => {
        throw new AuthenticationException("Invalid authentication type provided: " + t)
      }
    }).headOption
  }
}
