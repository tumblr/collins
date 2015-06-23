package collins.util.security

import java.util.concurrent.TimeUnit

import collins.models.User

import com.google.common.cache.CacheBuilder
import com.google.common.cache.CacheLoader
import com.google.common.cache.LoadingCache

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
  val cache: LoadingCache[Credentials, Option[User]] = CacheBuilder.newBuilder()
                                .maximumSize(100)
                                .expireAfterWrite(cacheTimeout, TimeUnit.MILLISECONDS)
                                .build(
                                  new CacheLoader[Credentials, Option[User]] {
                                    override def load(creds: Credentials): Option[User] = {
                                      logger.info("Loading user %s from backend".format(creds._1))
                                      authenticate(creds._1, creds._2)
                                    }
                                  }
                                )

  def tryAuthCache(provider: AuthenticationProvider, username: String, password: String): Option[User] = {
    if (!useCachedCredentials) {
      provider.authenticate(username, password)
    } else {
      cache.get((username, password)) match {
        case None =>
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

    providers.flatMap { p => tryAuthCache(p, username, password) }.headOption
  }
}
