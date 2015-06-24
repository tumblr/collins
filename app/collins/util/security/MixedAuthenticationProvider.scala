package collins.util.security

import java.util.concurrent.Callable
import java.util.concurrent.TimeUnit

import collins.models.User
import com.google.common.cache.CacheBuilder
import com.google.common.cache.Cache

/**
 * Exception encountered during authentication phase
 */
class AuthenticationException(msg: String) extends Exception(msg)

/**
 * Provides authentication by a variety of methods and caching logic (implements decorator pattern)
 */
class MixedAuthenticationProvider(types: Array[String]) extends AuthenticationProvider {
  
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
  
  /* Implement caching semantics for authentication */
  type Credentials = Tuple2[String,String]
  val cache: Cache[Credentials, Option[User]] = CacheBuilder.newBuilder()
                                .maximumSize(100)
                                .expireAfterWrite(cacheTimeout, TimeUnit.MILLISECONDS)
                                .build()

  def tryAuthCache(provider: AuthenticationProvider, username: String, password: String): Option[User] = {
    if (!useCachedCredentials) {
      provider.authenticate(username, password)
    } else {
      cache.get((username, password), new Callable[Option[User]] {
        override def call = {
          logger.info("Loading user %s from backend".format(username))
          provider.authenticate(username, password)
        }
      }) match {
        case None =>
          // if authentication failed, None will be present in the cache
          // due to the loader, discard it
          cache.invalidate((username, password))
          None
        case Some(u) =>
          Some(u)
      }
    }
  }

  /**
   * Attempt to authenticate user by each method specified in :types
   * @return Authenticated user or none
   */
  def authenticate(username: String, password: String): Option[User] = {
    logger.debug("Beginning to try authentication types")

    providers.toStream.flatMap { p => tryAuthCache(p, username, password) }.headOption
  }
}
