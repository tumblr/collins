package controllers

import play.api.Logger
import play.api.http.HeaderNames
import play.api.mvc._
import play.api.mvc.Results._
import play.api.mvc.Security._
import play.api.templates.Txt
import play.api.libs.iteratee._

import models.User
import util._

import org.apache.commons.codec.binary.Base64

/**
 * Provide a secure controller implementation.
 *
 * Handles both authenticated actions as well as role based authorization.
 *
 * Use like:
 *
 * def index = SecureAction { req =>
 * }(SecuritySpec(isSecure = true, requiredCredentials = Nil))
 */
object SecureController {
  private[this] val logger = Logger.logger

  def Authenticated[A](
    authenticate: RequestHeader => Option[User],
    onUnauthorized: RequestHeader => Result,
    hasRole: (User, Seq[String]) => Boolean)(action: Option[User] => Action[A])(implicit spec: SecuritySpecification): Action[(Action[A], A)] = {
    val authenticatedBodyParser = BodyParser { request =>
      spec.isSecure match {
        case false =>
          logger.debug("No authentication required, processing action")
          getAction(None, action, request)
        case true =>
          authenticate(request) match {
            case Some(user) =>
              logger.debug("Auth required and successful")
              spec.requiredCredentials match {
                case Nil =>
                  logger.debug("No credentials required, processing action")
                  getAction(Some(user), action, request)
                case seq => hasRole(user, seq) match {
                  case true =>
                    logger.debug("Credentials required and found, processing action")
                    getAction(Some(user), action, request)
                  case false =>
                    logger.debug("Credentials required and NOT found")
                    Done(Left(onUnauthorized(request)), Input.Empty)
                }
              }
            case None =>
              logger.debug("Auth required and NOT successful")
              Done(Left(onUnauthorized(request)), Input.Empty)
          }
      }
    }
    Action(authenticatedBodyParser) { request =>
      val (innerAction, innerBody) = request.body
      innerAction(request.map(_ => innerBody))
    }
  }
  private def getAction[A](u: Option[User], a: Option[User] => Action[A], r: RequestHeader) = {
    val innerAction = a(u)
    innerAction.parser(r).mapDone { body =>
      body.right.map(innerBody => (innerAction, innerBody))
    }
  }
}

trait SecureController extends Controller {
  protected val logger = Logger.logger

  /** Controllers that extend this trait can override the default hasRole behavior */
  protected def hasRole(user: User, roles: Seq[String]): Boolean = {
    user.roles.intersect(roles).size == roles.size
  }
  /** Authenticate a request, return a User if the request can be authenticated */
  protected def authenticate(request: RequestHeader): Option[User]
  /** Where to go if a request can't be authenticated */
  protected def onUnauthorized(request: RequestHeader): Result = Unauthorized(views.html.defaultpages.unauthorized())

  def Authenticated[A](action: Option[User] => Action[A])(implicit spec: SecuritySpecification) =
    SecureController.Authenticated(authenticate, onUnauthorized, hasRole)(action)

  def SecureAction(block: Request[AnyContent] => Result)(implicit spec: SecuritySpecification) =
    Authenticated(_ => Action(block))
}

/** Used for regular web access, authenticates based on session */
trait SecureWebController extends SecureController {
  val unauthorizedRoute = routes.Application.login
  def securityMessage(req: RequestHeader) = ("security" -> "The specified resource requires additional authorization")

  override def onUnauthorized(request: RequestHeader) =
    Results.Redirect(unauthorizedRoute).flashing(securityMessage(request))

  /** Use sessions storage for authenticate/etc */
  override def authenticate(request: RequestHeader) = User.fromMap(request.session.data) match {
    case Some(user) => user.isAuthenticated match {
      case true =>
        Some(user)
      case false =>
        logger.debug("SecureWebController.authenticate: user found, not authenticated")
        None
    }
    case None =>
      logger.debug("SecureWebController.authenticate: user not found, session data not found")
      None
  }
}

/** Used for API access, authenticates based on basic auth */
trait SecureApiController extends SecureController {
  override def onUnauthorized(request: RequestHeader) = {
    Results.Unauthorized(Txt("Invalid Username/Password specified"))
  }

  /** Do not use session storage for authenticate */
  override def authenticate(request: RequestHeader): Option[User] = {
    request.headers.get(HeaderNames.AUTHORIZATION) match {
      case None =>
        logger.debug("Got API request with no auth header")
        None
      case Some(header) =>
        try {
          parseAuthHeader(header) match {
            case None =>
              logger.debug("Failed to authenticate request")
              None
            case Some(u) =>
              logger.debug("Logged in user %s".format(u.username))
              Some(u)
          }
        } catch {
          case e: Throwable =>
            logger.warn("Caught exception authenticating user: " + e.getMessage)
            None
        }
    }
  }

  protected def parseAuthHeader(header: String): Option[User] = {
    header.split(" ").toList match {
      case "Basic" :: base64encoded :: Nil =>
        val decodedBase64 = Base64.decodeBase64(base64encoded.getBytes)
        val decoded = new String(decodedBase64)
        decoded.split(":").toList match {
          case u :: tail  => User.authenticate(u, tail.mkString(":"))
          case _ => throw new IllegalArgumentException("No username:password found")
        }
      case _ => throw new IllegalArgumentException("Only Basic Auth is supported")
    }
  }

}
