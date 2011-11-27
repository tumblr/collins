package controllers

import play.api._
import play.api.data._
import play.api.mvc._

import models._
import views._

object Application extends Controller {
 
  val loginForm = Form(
    of(
      "username" -> requiredText(1),
      "password" -> requiredText(3)
      ) verifying ("Invalid username or password", result => result match {
        case(username,password) => User.authenticate(username, password)
      })
  )

  def login = Action { implicit req =>
    Ok(html.login(loginForm))
  }

  def authenticate = Action { implicit req =>
    loginForm.bindFromRequest.fold(
      formWithErrors =>
        BadRequest(html.login(
          formWithErrors.bind(formWithErrors.data - "password")
        )),
      user => Redirect(routes.Resources.index).withSession(Security.USERNAME -> user._1)
    )
  }

  def logout = Security.Authenticated {
    Action { implicit req =>
      Redirect(routes.Application.login).withNewSession.flashing(
        "success" -> "You have been logged out"
      )
    }
  }

}
