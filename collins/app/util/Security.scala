package util

import play.api._
import models.{User, UserImpl}
import annotation.implicitNotFound

@implicitNotFound(msg = "Didn't find an implicit SecuritySpecification but expected one")
trait SecuritySpecification {
  val isSecure: Boolean
  val requiredCredentials: Seq[String]
}
case class SecuritySpec(isSecure: Boolean, requiredCredentials: Seq[String])
  extends SecuritySpecification

trait AuthenticationProvider {
  protected val logger = Logger.logger
  def authenticate(username: String, password: String): Option[User]
}
trait AuthenticationAccessor {
  def getAuthentication(): AuthenticationProvider
}

object AuthenticationProvider {
  val Default = new MockAuthenticationProvider
  val Types = Set("ldap", "default")
  def get(name: String, config: Configuration): AuthenticationProvider = {
    name match {
      case "ldap" =>
        new LdapAuthenticationProvider(config)
      case "default" =>
        Default
    }
  }
}

class LdapAuthenticationProvider(config: Configuration) extends AuthenticationProvider {
  import scala.collection.JavaConversions._
  import scala.collection.JavaConverters._
  import java.util.{Hashtable => JHashTable}
  import javax.naming._
  import javax.naming.directory._

  // validation
  require(config.getString("host").isDefined, "LDAP requires a host attribute")
  require(config.getString("searchbase").isDefined, "LDAP requires a searchbase attribute")
  config.getString("type").map { cfg =>
    require(cfg.toLowerCase == "ldap", "If specified, authentication type must be LDAP")
  }

  // LDAP values
  val host = config.getString("host").get
  val searchbase = config.getString("searchbase").get
  val url = "ldap://%s/%s".format(host, searchbase)
  logger.debug("LDAP URL: %s".format(url))

  // setup for LDAP
  private val env = Map(
    Context.INITIAL_CONTEXT_FACTORY -> "com.sun.jndi.ldap.LdapCtxFactory",
    Context.PROVIDER_URL -> url,
    Context.SECURITY_AUTHENTICATION -> "simple")

  // Authenticate via LDAP
  override def authenticate(username: String, password: String): Option[User] = {
    val principal = "uid=%s,cn=users".format(username)
    val userEnv = Map(
      Context.SECURITY_PRINCIPAL -> (principal + "," + searchbase),
      Context.SECURITY_CREDENTIALS -> password) ++ env

    var ctx: InitialDirContext = null
    try {
      ctx = new InitialDirContext(new JHashTable[String,String](userEnv.asJava))
      val uid = getUid(principal, ctx)
      require(uid > 0, "Unable to find UID for user")
      val groups = getGroups(username, ctx)
      val user = UserImpl(username, "*", groups.map { _._2 }.toSeq, uid, true)
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
    val query = "(&(cn=*)(memberUid=%s))".format(username)
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

class MockAuthenticationProvider extends AuthenticationProvider {
  val users = Map(
    "blake" -> UserImpl("blake", "admin:first", Seq("engineering","infra","ops"), 1024, false),
    "matt" -> UserImpl("matt", "foobar", Seq("engineering", "management"), 1025, false),
    "test" -> UserImpl("test", "fizz", Nil, 1026, false)
  )

  override def authenticate(username: String, password: String): Option[User] = {
    users.get(username) match {
      case None => None
      case Some(user) => (password == user.password) match {
        case true =>
          val newUser = user.copy(_password = "*", _authenticated = true)
          Some(newUser)
        case false => None
      }
    }
  }
}
