package collins.util.security

import java.util.concurrent.Callable
import java.util.concurrent.TimeUnit
import collins.models.User
import com.google.common.cache.CacheBuilder
import com.google.common.cache.Cache
import collins.guava.GuavaCacheFactory

/**
 * Exception encountered during authentication phase
 */
class AuthenticationException(msg: String) extends Exception(msg)

/**
 * Provides authentication by a variety of methods and caching logic (implements decorator pattern)
 */
class MixedAuthenticationProvider(types: List[String]) extends AuthenticationProvider {
  
  private val providers = types.map({
     case "default" => {
       logger.trace("mock authentication type")
       new MockAuthenticationProvider
     }
     case "file" => {
       logger.trace("file authentication type")
       new FileAuthenticationProvider()
     }
     case "ldap" => {
       logger.trace("ldap authentication type")
       new LdapAuthenticationProvider()
     }
     case t => {
       throw new AuthenticationException("Invalid authentication type provided: " + t)
     }
  })
  
  def authType = types

  /**
   * Attempt to authenticate user by each method specified in :types
   * @return Authenticated user or none
   */
  def authenticate(username: String, password: String): Option[User] = {
    logger.debug("Beginning to try authentication types")

    providers.toStream.flatMap { _.authenticate(username, password) }.headOption
  }
}
