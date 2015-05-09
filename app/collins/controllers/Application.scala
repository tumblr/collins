package collins.controllers

import play.api.data.Form
import play.api.data.Forms
import play.api.mvc.Action

import collins.models.User
import collins.util.security.SecuritySpec

import views.html

object Application extends SecureWebController {
 
  val loginForm = Form(
    Forms.tuple(
      "username" -> Forms.nonEmptyText,
      "password" -> Forms.nonEmptyText(3),
      "location" -> Forms.optional(Forms.text)
      ) verifying ("Invalid username or password", result => result match {
        case(username,password,location) =>
          User.authenticate(username, password).isDefined
      })
  )

  def login = Action { implicit req =>
    req.queryString.get("location") match {
      case None =>
        Ok(html.login(loginForm))
      case Some(location) =>
        Ok(html.login(loginForm.fill(("","",Some(location.head))).copy(errors = Nil)))
    }
  }

  def authenticate = Action { implicit req =>
    loginForm.bindFromRequest.fold(
      formWithErrors => {
        val tmp: Map[String,String] = formWithErrors.data - "password"
        BadRequest(html.login(formWithErrors.copy(data = tmp)))
      },
      user => {
        val u = User.toMap(User.authenticate(user._1, user._2))
        user._3 match {
          case Some(location) =>
            Redirect(location).withSession(u.toSeq:_*)
          case None =>
            Redirect(collins.app.routes.Resources.index).withSession(u.toSeq:_*)
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
