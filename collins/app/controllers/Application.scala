package controllers

import play.api._
import play.api.data._
import play.api.mvc._

import models._
import util.SecuritySpec
import views._

object Application extends SecureWebController {
 
  val loginForm = Form(
    of(
      "username" -> requiredText(1),
      "password" -> requiredText(3)
      ) verifying ("Invalid username or password", result => result match {
        case(username,password) =>
          User.authenticate(username, password).isDefined
      })
  )

  def login = Action { implicit req =>
    Ok(html.login(loginForm))
  }

  def authenticate = Action { implicit req =>
    loginForm.bindFromRequest.fold(
      formWithErrors => {
        val tmp: Map[String,String] = formWithErrors.data - "password"
        BadRequest(html.login(formWithErrors.copy(data = tmp)))
      },
      user => {
        val u = User.toMap(User.authenticate(user._1, user._2))
        Redirect(app.routes.Resources.index).withSession(u.toSeq:_*)
      }
    )
  }

  def logout = SecureAction { implicit req =>
    Redirect(routes.Application.login).withNewSession.flashing(
      "success" -> "You have been logged out"
    )
  }(SecuritySpec(isSecure = true, Nil))

}
