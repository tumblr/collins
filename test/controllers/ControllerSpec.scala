package controllers

import models.{User, UserImpl}
import play.api.mvc._
import play.api.http.HeaderNames
import play.api.templates.Txt

trait ControllerSpec {
  def getApi(user: Option[User]) = new Api with SecureController {
    override def authenticate(request: RequestHeader) = user
    override def onUnauthorized = Action { req =>
      Results.Unauthorized(Txt("Invalid username/password specified"))
    }
  }
  def getLoggedInUser(group: String) = Some(UserImpl("test", "*", Set(group), 123, true))
}
