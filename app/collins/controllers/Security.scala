package collins.controllers

import org.apache.commons.codec.binary.Base64

import play.api.Logger
import play.api.http.HeaderNames
import play.api.mvc.Action
import play.api.mvc.AnyContent
import play.api.mvc.Controller
import play.api.mvc.Request
import play.api.mvc.RequestHeader
import play.api.mvc.SimpleResult
import play.api.mvc.Results
import play.api.templates.Txt

import collins.models.User
import collins.util.security.AuthenticationProvider
import collins.util.security.SecuritySpecification

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

  def Authenticated(
    authenticate: RequestHeader => Option[User],
    onUnauthorized: Action[AnyContent],
    authorize: (User, SecuritySpecification) => Boolean)(action: Option[User] => Action[AnyContent])(implicit spec: SecuritySpecification): Action[AnyContent] = {
    val newAction = Action.async { request =>
      spec.isSecure match {
        case false =>
          logger.debug("No authentication required, processing action")
          getAction(None, action, request)
        case true =>
          authenticate(request) match {
            case Some(user) =>
              logger.debug("Auth required and successful")
              if (spec.requiredCredentials.isEmpty) {
                logger.debug("No credentials required, processing action")
                getAction(Some(user), action, request)
              } else {
                if (authorize(user, spec)) {
                  logger.debug("Credentials required and found, processing action")
                  getAction(Some(user), action, request)
                } else {
                  logger.warn("Credentials required for %s and NOT found for %s".format(request.path, user.username))
                  onUnauthorized(request)
                }
              }
            case None =>
              logger.debug("Auth required and NOT successful")
              onUnauthorized(request)
          }
      }
    }
    newAction
  }
  private def getAction(u: Option[User], a: Option[User] => Action[AnyContent], r: Request[AnyContent]) = {
   a(u)(r)
  }
}

trait SecureController extends Controller {
  protected val logger = Logger.logger

  /** Controllers that extend this trait can override the default authorize behavior */
  def authorize(user: User, spec: SecuritySpecification): Boolean = {
    AuthenticationProvider.userIsAuthorized(user, spec)
  }
  /** Authenticate a request, return a User if the request can be authenticated */
  def authenticate(request: RequestHeader): Option[User]
  /** Where to go if a request can't be authenticated */
  def onUnauthorized: Action[AnyContent]

  def Authenticated(action: Option[User] => Action[AnyContent])(implicit spec: SecuritySpecification) =
    SecureController.Authenticated(authenticate, onUnauthorized, authorize)(action)

  def SecureAction(block: Request[AnyContent] => SimpleResult)(implicit spec: SecuritySpecification) =
    Authenticated(_ => Action(block))
}

/** Used for regular web access, authenticates based on session */
trait SecureWebController extends SecureController {
  val unauthorizedRoute = routes.Application.login.url
  def securityMessage(req: RequestHeader) = ("security" -> "The specified resource requires additional authorization")

  override def onUnauthorized = Action { implicit request =>
    // If user is not logged in and accesses a page, store the location so they can be redirected
    // after authentication
    if (User.fromMap(request.session.data).isDefined) {
      Results.Redirect("/").flashing(securityMessage(request))
    } else {
      if (request.path != "/login") {
        Results.Redirect(unauthorizedRoute + "?location=" + request.path)
      } else { // Otherwise they really don't have permissions
        Results.Redirect(unauthorizedRoute)
      }
    }
  }

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
  override def onUnauthorized = Action { implicit request =>
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
