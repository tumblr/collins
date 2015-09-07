package collins.controllers

import play.api.data.Form
import play.api.data.Forms
import play.api.mvc.Action
import play.api.mvc.AnyContent
import play.api.mvc.Request

import collins.models.User
import collins.util.security.SecuritySpec

import views.html

object Application extends SecureWebController {

  val loginForm = Form(
    Forms.tuple(
      "username" -> Forms.nonEmptyText,
      "password" -> Forms.nonEmptyText(3),
      "location" -> Forms.optional(Forms.text)
    )
  )

  def login = Action { implicit req =>
    req.queryString.get("location") match {
      case None =>
        Ok(html.login(loginForm))
      case Some(location) =>
        Ok(html.login(loginForm.fill(("","",Some(location.head))).copy(errors = Nil)))
    }
  }

  def sanitizeForm(loginForm : Form[(String, String, Option[String])]): Map[String,String] = loginForm.data - "password"

  def failedAuth(loginForm : Form[(String, String, Option[String])]) (implicit req: Request[AnyContent]) = {
    BadRequest(html.login(loginForm.withGlobalError("Invalid username or password").copy(data = sanitizeForm(loginForm))))
  }

  def successAuth(user: User, location: Option[String]) = {
    location match {
      case Some(location) =>
        Redirect(location).withSession(User.toMap(Some(user)).toSeq:_*)
      case None =>
        Redirect(collins.app.routes.Resources.index).withSession(User.toMap(Some(user)).toSeq:_*)
    }
  }

  def authenticate = Action { implicit req =>
    loginForm.bindFromRequest.fold(
      formWithErrors => {
        BadRequest(html.login(formWithErrors.copy(data = sanitizeForm(formWithErrors))))
      },
      userForm => {
        User.authenticate(userForm._1, userForm._2) match {
          case None => failedAuth(loginForm)
          case Some(user) => successAuth(user, userForm._3)
        }
      }
    )
  }

  def logout = SecureAction { implicit req =>
    Redirect(routes.Application.login).withNewSession.flashing(
      "success" -> "You have been logged out"
    )
  }(SecuritySpec(true))

}
