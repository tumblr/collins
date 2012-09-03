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
  override val authType = "ldap"

  // LDAP values
  def host = config.host
  def searchbase = config.searchbase
  def useSsl = config.useSsl match {
    case true => "ldaps"
    case _ => "ldap"
  }
  def url = "%s://%s/%s".format(useSsl, host, searchbase)
  def usersub = config.usersub
  def groupsub = config.groupsub
  def groupattrib = config.groupAttribute

  // setup for LDAP
  protected def env = Map(
    Context.INITIAL_CONTEXT_FACTORY -> "com.sun.jndi.ldap.LdapCtxFactory",
    Context.PROVIDER_URL -> url,
    Context.SECURITY_AUTHENTICATION -> "simple")

  protected def getPrincipal(username: String): String = {
    "uid=%s,%s".format(username, usersub)
  }

  protected def getSecurityPrincipal(username: String): String = {
    "%s,%s".format(getPrincipal(username), searchbase)
  }

  protected def groupQuery(username: String): String = {
    "(&(cn=*)(%s=%s))".format(groupattrib, getSecurityPrincipal(username))
  }

  logger.debug("LDAP URL: %s".format(url))

  // Authenticate via LDAP
  override def authenticate(username: String, password: String): Option[User] = {
    val userEnv = Map(
      Context.SECURITY_PRINCIPAL -> getSecurityPrincipal(username),
      Context.SECURITY_CREDENTIALS -> password) ++ env

    var ctx: InitialDirContext = null
    try {
      ctx = new InitialDirContext(new JHashTable[String,String](userEnv.asJava))
      val uid = getUid(getPrincipal(username), ctx)
      require(uid > 0, "Unable to find UID for user")
      val groups = getGroups(username, ctx)
      val user = UserImpl(username, "*", groups.map { _._2 }.toSet, uid, true)
      logger.trace("Succesfully authenticated %s".format(username))
      Some(user)
    } catch {
      case e: AuthenticationException =>
        logger.info("Failed authentication for user %s".format(username))
        None
      case e: Throwable =>
        logger.info("Failed authentication", e)
        None
    } finally {
      if (ctx != null) ctx.close
    }
  }

  // Return UUID
  protected def getUid(search: String, ctx: InitialDirContext): Int = {
    val ctrl = new SearchControls
    ctrl.setSearchScope(SearchControls.OBJECT_SCOPE)
    val attribs = ctx.getAttributes(search)
    attribs.get("uidNumber") match {
      case null => -1
      case attrib => attrib.get.asInstanceOf[String].toInt
    }
  }

  protected def getGroups(username: String, ctx: InitialDirContext): Seq[(Int,String)] = {
    val ctrl = new SearchControls
    ctrl.setSearchScope(SearchControls.SUBTREE_SCOPE)
    val query = groupQuery(username)
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
