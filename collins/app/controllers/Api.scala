package controllers

import play.api._
import play.api.mvc._
import models.User
import util._

object Api extends SecureApiController {

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

}
