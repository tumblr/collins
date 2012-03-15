package util

import play.api._
import models.{User, UserImpl}
import annotation.implicitNotFound

@implicitNotFound(msg = "Didn't find an implicit SecuritySpecification but expected one")
trait SecuritySpecification {
  val isSecure: Boolean
  val requiredCredentials: Seq[String]
}
case class SecuritySpec(isSecure: Boolean, requiredCredentials: Seq[String]) extends SecuritySpecification
object SecuritySpec {
  def apply(isSecure: Boolean, requiredCredentials: String) =
    new SecuritySpec(isSecure, Seq(requiredCredentials))
  def apply(isSecure: Boolean) = new SecuritySpec(isSecure, Nil)
}

trait AuthenticationProvider {
  protected val logger = Logger.logger
  def authenticate(username: String, password: String): Option[User]
}
trait AuthenticationAccessor {
  def getAuthentication(): AuthenticationProvider
}

object AuthenticationProvider {
  val Default = new MockAuthenticationProvider
  val Types = Set("ldap", "file", "default", "ipa")
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
}

class IpaAuthenticationProvider(config: Configuration) extends LdapAuthenticationProvider(config) {
  override protected def groupQuery(username: String): String = {
    "(&(cn=*)(member=%s))".format(getSecurityPrincipal(username))
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
  require(config.getString("usersub").isDefined, "LDAP requires a usersub attribute")
  require(config.getString("groupsub").isDefined, "LDAP requires a groupsub attribute")

  // LDAP values
  val host = config.getString("host").get
  val searchbase = config.getString("searchbase").get
  val useSsl = config.getBoolean("ssl") match {
    case Some(true) => "ldaps"
    case _ => "ldap"
  }
  val url = "%s://%s/%s".format(useSsl, host, searchbase)
  val usersub = config.getString("usersub").get
  val groupsub = config.getString("groupsub").get
  logger.debug("LDAP URL: %s".format(url))

  // setup for LDAP
  protected val env = Map(
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
    "(&(cn=*)(memberUid=%s))".format(username)
  }

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

class FileAuthenticationProvider(config: Configuration) extends AuthenticationProvider {
  import scala.io.Source
  import sun.misc.BASE64Encoder
  import java.io.File
  import java.security.MessageDigest

  require(config.getString("file").isDefined, "FILE requires an authentication.file attribute")
  config.getString("type").map { cfg =>
    require(cfg.toLowerCase == "file", "If specified, authentication type must be file")
  }
  val file = new File(config.getString("file").get)
  require(file.exists() && file.canRead(), "File %s does not exist".format(config.getString("file").get))

  val users = {
    val _users = Source.fromFile(file, "UTF-8").getLines().map { line =>
      val split = line.split(":", 3)
      if (split.length != 3) {
        throw new Exception("Invalid line format for users")
      }
      val username = split(0)
      val password = split(1)
      val roles = split(2).split(",").toSeq
      UserImpl(username, password, roles, username.hashCode, false)
    }.toSeq
    _users.map { user =>
      user.username -> user
    }.toMap
  }

  override def authenticate(username: String, password: String): Option[User] = {
    users.get(username) match {
      case None => None
      case Some(user) => (hash(password) == user.password) match {
        case true =>
          val newUser = user.copy(_password = "*", _authenticated = true)
          Some(newUser)
        case false => None
      }
    }
  }

  // This is consistent with how apache encrypts SHA1
  protected def hash(s: String): String = {
    "{SHA}" + new BASE64Encoder().encode(MessageDigest.getInstance("SHA1").digest(s.getBytes()))
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
