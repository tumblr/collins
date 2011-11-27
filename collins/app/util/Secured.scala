package util

import play.api.Logger
import play.api.http.HeaderNames
import play.api.mvc._
import play.api.mvc.Security._
import play.api.templates.Txt

import controllers._
import models.User

import org.apache.commons.codec.binary.Base64

trait RoleBasedSecureController extends Controller with Security.AllAuthenticated {

  def hasRole(req: RequestHeader, role: String): Boolean = {
    req.session.get("roles").isDefined && req.session.get("roles").get.contains(role)
  }

  def Authorized[A](requiredRole: String)(action: Action[A]): Action[A] = {
    action.compose { (request, originalAction) =>
      hasRole(request, requiredRole) match {
        case true =>
          originalAction(request)
        case false =>
          onUnauthorized(request)
      }
    }
  }
}

trait SecuredWebController extends RoleBasedSecureController {
  val logger = Logger.logger
  override def onUnauthorized(request: RequestHeader) = Results.Redirect(routes.Application.login)
}

trait SecuredApiController extends RoleBasedSecureController {
  val logger = Logger.logger

  override def onUnauthorized(request: RequestHeader) = {
    Results.Unauthorized(Txt("Invalid Username/Password specified"))
  }

  override def username(request: RequestHeader): Option[String] = {
    request.headers.get(HeaderNames.AUTHORIZATION) match {
      case None =>
        logger.info("Got API request with no auth header")
        None
      case Some(header) =>
        try {
          val user = parseAuthHeader(header)
          user.authenticate() match {
            case true => Some(user.username)
            case false => None
          }
        } catch {
          case e: Throwable =>
            logger.warn("Caught exception authenticating user: " + e.getMessage)
            None
        }
    }
  }

  protected def parseAuthHeader(header: String): User = {
    header.split(" ").toList match {
      case "Basic" :: base64encoded :: Nil =>
        val decodedBase64 = Base64.decodeBase64(base64encoded.getBytes)
        val decoded = new String(decodedBase64)
        decoded.split(":").toList match {
          case u :: tail  => User(u, tail.mkString(":"))
          case _ => throw new IllegalArgumentException("No username:password found")
        }
      case _ => throw new IllegalArgumentException("Only Basic Auth is supported")
    }
  }

}
