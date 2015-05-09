package collins.util.security

import java.util.{Hashtable => JHashTable}

import scala.collection.JavaConversions.enumerationAsScalaIterator
import scala.collection.JavaConversions.mapAsJavaMap
import scala.collection.JavaConverters.mapAsJavaMapConverter

import collins.models.User
import collins.models.UserImpl

import javax.naming.Context
import javax.naming.directory.InitialDirContext
import javax.naming.directory.SearchControls
import javax.naming.directory.SearchResult

class LdapAuthenticationProvider extends AuthenticationProvider {

  val config = LdapAuthenticationProviderConfig
  override val authType = "ldap"

  // LDAP values
  def host = config.host
  def searchbase = config.searchbase
  def useSsl = config.useSsl match {
    case true => "ldaps"
    case _ => "ldap"
  }
  def url = "%s://%s".format(useSsl, host)
  def usersub = config.usersub
  def groupsub = config.groupsub
  def groupattrib = config.groupAttribute
  def userattrib = config.userAttribute
  def binddn = config.binddn
  def bindpwd = config.bindpwd
  def anonymous = config.anonymous
  def groupnameattrib = config.groupNameAttribute
  def usernumattrib = config.userNumberAttribute

  // setup for LDAP
  protected def env = Map(
    Context.INITIAL_CONTEXT_FACTORY -> "com.sun.jndi.ldap.LdapCtxFactory",
    Context.PROVIDER_URL -> url,
    Context.SECURITY_AUTHENTICATION -> "simple")

  protected def groupQuery(dn: String, username: String): String = {
    if (config.isRfc2307Bis)
      "(&(%s=*)(%s=%s))".format(groupnameattrib, groupattrib, dn)
    else
      "(&(%s=*)(%s=%s))".format(groupnameattrib, groupattrib, username)
  }

  logger.debug("LDAP URL: %s".format(url))
  
  private def getInitialContext() : Option[InitialDirContext] = {
    try {
      if (anonymous) {
        Some(new InitialDirContext(new JHashTable[String, String](env.asJava)))
      } else {
        Some(new InitialDirContext(new JHashTable[String, String](
          Map(
            Context.SECURITY_PRINCIPAL -> binddn,
            Context.SECURITY_CREDENTIALS -> bindpwd) ++ env
        )))
      }
    } catch {
      case e: AuthenticationException =>
        logger.error("Failed to create directory context, authentication failed", e)
        None
      case e: Throwable =>
        logger.error("Failed to create directory context", e)
        None
    }
  }
    
  private def findDn(ctx: InitialDirContext, username: String): Option[String] = {
    val searchControls = new SearchControls
    searchControls.setSearchScope(SearchControls.SUBTREE_SCOPE)
    val filter = "%s=%s".format(userattrib, username)
    val searchRoot = "%s,%s".format(usersub, searchbase)
    val res = ctx.search(searchRoot, filter, searchControls)
    
    if (res.hasMoreElements()) {
      val sr = res.nextElement()
      
      if (res.hasMoreElements()) {
        logger.warn("Multiple search results when authenticating %s".format(username))
        None
      } else {
        Some(sr.getNameInNamespace())
      }
    } else {
      logger.warn("No search results when authentication %s".format(username))
      None
    }
  }
  
  // creating a initial dir context will fail to indicate an authentication error
  private def getUserContext(dn: String,  password: String) = {
    new InitialDirContext(new JHashTable[String, String](
      Map(
        Context.SECURITY_PRINCIPAL -> dn,
        Context.SECURITY_CREDENTIALS -> password) ++ env
    ))
  }

  // Authenticate via LDAP
  override def authenticate(username: String, password: String): Option[User] = {
    getInitialContext().flatMap (ctx => {
      try {
        findDn(ctx, username).map(dn => {
          val uctx = getUserContext(dn, password)
          try {
            val uid = getUid(dn, uctx)
            require(uid > 0, "Unable to find UID for user")
            val groups = getGroups(dn, username, uctx)
            val user = UserImpl(username, "*", groups.toSet, uid, true)
            logger.trace("Succesfully authenticated %s".format(username))
            user
          } finally {
            uctx.close
          }
        })
      } catch {
        case e: AuthenticationException =>
          logger.info("Failed authentication for user %s", e)
          None        
      } finally {
    	ctx.close
      }
    })
  }

  // Return UUID
  protected def getUid(dn: String, ctx: InitialDirContext): Int = {
    val ctrl = new SearchControls
    ctrl.setSearchScope(SearchControls.OBJECT_SCOPE)
    val attribs = ctx.getAttributes(dn)
    attribs.get(usernumattrib) match {
      case null => -1
      case attrib => attrib.get.asInstanceOf[String].toInt
    }
  }

  protected def getGroups(dn: String, username: String, ctx: InitialDirContext): Seq[String] = {
    val ctrl = new SearchControls
    ctrl.setSearchScope(SearchControls.SUBTREE_SCOPE)
    val query = groupQuery(dn, username)
    val searchRoot = "%s,%s".format(groupsub, searchbase)
    val it = for (
         result <- ctx.search(searchRoot, query, ctrl);
         attribs = result.asInstanceOf[SearchResult].getAttributes();
         if attribs.get(groupnameattrib) != null;
         groupname = attribs.get(groupnameattrib).get.asInstanceOf[String]
       ) yield groupname
    it.toSeq
  }
}
