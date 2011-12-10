package controllers

import play.api._
import play.api.mvc._
import models.User
import util._

trait Api extends Controller {
  this: SecureController =>

  implicit val securitySpec = SecuritySpec(isSecure = true, requiredCredentials = Nil)

  def authenticatedPing = SecureAction { implicit req =>
    Ok("You are logged in")
  }

  def authorizedPing = SecureAction { implicit req =>
    Ok("You are logged in with the right role")
  }(SecuritySpec(isSecure = true, Seq("engineering")))

  def noauthPing = SecureAction { implicit req =>
    Ok("No auth required")
  }(SecuritySpec(isSecure = false, Nil))

  def getOutputType(request: Request[AnyContent]): OutputType = OutputType(request) match {
    case Some(ot) => ot
    case None => throw new Exception("WTF?")
  }
}
