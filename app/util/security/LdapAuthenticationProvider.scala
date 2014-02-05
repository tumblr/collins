package util
package security

import models.{User, UserImpl}

import scala.collection.JavaConversions._
import scala.collection.JavaConverters._
import java.util.{Hashtable => JHashTable}
import javax.naming._
import javax.naming.directory._

class LdapAuthenticationProvider() extends AuthenticationProvider {

  val config = LdapAuthenticationProviderConfig
  val authType = "ldap"

  private def useSsl = config.useSsl match {
    case true => "ldaps"
    case _ => "ldap"
  }
  private def host = config.host
  private def searchbase = config.searchbase
  private def userAttribute = config.userAttribute
  private def groupAttribute = config.groupAttribute
  private def usersub = config.usersub
  // TODO This is not used anywhere, remove from here and docs
  private def groupsub = config.groupsub

  private def url = "%s://%s/%s".format(useSsl, host, searchbase)

  // Configuration for ldap connection context
  protected def env = Map(
    Context.INITIAL_CONTEXT_FACTORY -> "com.sun.jndi.ldap.LdapCtxFactory",
    Context.PROVIDER_URL -> url,
    Context.SECURITY_AUTHENTICATION -> "simple"
  )

  /**
   * The relative distinguished name
   * @param username
   * @return E.g. uid=jdoe,ou=people
   */
  protected def getPrincipal(username: String): String = {
    "%s=%s,%s".format(userAttribute, username, usersub)
  }

  /**
   * The complete distinguished name
   * @param username
   * @return E.g. uid=jdoe,ou=people,dc=example,dc=org
   */
  protected def getSecurityPrincipal(username: String): String = {
    "%s,%s".format(getPrincipal(username), searchbase)
  }

  /**
   * @param username
   * @return Search filter for user; e.g. (&cn=*)(uniqueMember=uid=jdoe,ou=people,dc=example,dc=org)
   */
  protected def groupSearchFilter(username: String): String = {
    if (config.isRfc2307Bis)
      "(&(cn=*)(%s=%s))".format(groupAttribute, getSecurityPrincipal(username))
    else
      "(&(cn=*)(%s=%s))".format(groupAttribute, username)
  }

  logger.debug("LDAP URL: %s".format(url))

  /**
   * Authenticate user & password against configured ldap host
   * @param username
   * @param password
   * @return If authentication successful, the user; otherwise None
   */
  def authenticate(username: String, password: String): Option[User] = {
    logContextEnvInfo(username)

    val userEnv = Map(
      Context.SECURITY_PRINCIPAL -> getSecurityPrincipal(username),
      Context.SECURITY_CREDENTIALS -> password
    ) ++ env

    var ctx: InitialDirContext = null
    try {
      logger.trace("establishing context")
      ctx = new InitialDirContext(new JHashTable[String,String](userEnv.asJava))
      logger.trace("established context")

      val uid = getUid(username, ctx)
      require(uid > 0, "Unable to find UID for user")
      logger.debug("Found uid=%s for user %s".format(uid, username))

      val groups = getGroups(username, ctx)
      val user = UserImpl(username, "*", groups.map { _._2 }.toSet, uid, true)
      logger.trace("Succesfully authenticated %s".format(username))

      Some(user)
    } catch {
      case e: AuthenticationException =>
        logger.info("Failed authentication for user %s with error %s".format(username, e.getMessage))
        None
      case e: Throwable =>
        logger.info("Authentication process failed. Check configuration?", e)
        None
    } finally {
      if (ctx != null) ctx.close
    }
  }


  /**
   * INFO log environment and distinguished name details
   * @param username
   */
  private def logContextEnvInfo(username: String) {
    if (logger.isInfoEnabled) {
      val dn = getSecurityPrincipal(username)

      logger.info("Building context for dn -> %s with the following environment".format(dn))
      env.foreach {
        case (key, value) => {
          logger.info("Context env key %s -> %s".format(key, value))
        }
      }
    }
  }

  /**
   * @param username
   * @param ctx
   * @return Ldap user id
   */
  protected def getUid(username: String, ctx: InitialDirContext): Int = {
    val attribs = ctx.getAttributes(getPrincipal(username))

    attribs.get("uidNumber") match {
      case null => -1
      case attrib => attrib.get.asInstanceOf[String].toInt
    }
  }

  /**
   * @param username
   * @param ctx
   * @return Sequence of (group id, common name) tuples
   */
  protected def getGroups(username: String, ctx: InitialDirContext): Seq[(Int,String)] = {
    val ctrl = new SearchControls
    ctrl.setSearchScope(SearchControls.SUBTREE_SCOPE)
    val query = groupSearchFilter(username)

    val it = for (
      result <- ctx.search("", query, ctrl);
      attribs = result.asInstanceOf[SearchResult].getAttributes();
      if attribs.get("cn") != null;
      if attribs.get("gidNumber") != null;
      cn = attribs.get("cn").get.asInstanceOf[String];
      gidNumber = attribs.get("gidNumber").get.asInstanceOf[String].toInt
    ) yield(gidNumber, cn)

    it.toSeq
  }
}
