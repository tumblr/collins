package collins.controllers

import play.api.mvc.Action
import play.api.mvc.RequestHeader
import play.api.mvc.Results
import play.twirl.api.Txt

import collins.models.User
import collins.models.UserImpl
import collins.models.logs.LogSource

trait ControllerSpec {
  def getApi(user: Option[User]) = new Api with SecureController {
    override def authenticate(request: RequestHeader) = user
    override def logSource = LogSource.User
    override def onUnauthorized = Action { req =>
      Results.Unauthorized(Txt("Invalid username/password specified"))
    }
  }
  def getLoggedInUser(group: String) = Some(UserImpl("test", "*", Set(group), 123, true))
}
