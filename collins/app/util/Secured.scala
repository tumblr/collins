package util

import play.api.mvc._
import controllers._

trait Secured extends Security.AllAuthenticated {
  override def onUnauthorized(request: RequestHeader) = Results.Redirect(routes.Application.login)
}
