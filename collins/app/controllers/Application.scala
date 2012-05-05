package controllers

import play.api._
import play.api.data._
import play.api.data.Forms._
import play.api.mvc._

import models._
import util.SecuritySpec
import views._

object Application extends SecureWebController {
 
  val loginForm = Form(
    tuple(
      "username" -> nonEmptyText,
      "password" -> nonEmptyText(3),
      "location" -> optional(text)
      ) verifying ("Invalid username or password", result => result match {
        case(username,password,location) =>
          User.authenticate(username, password).isDefined
      })
  )

  def login = Action { implicit req =>
    setUser(None)
    req.queryString.get("location") match {
      case None =>
        Ok(html.login(loginForm))
      case Some(location) =>
        Ok(html.login(loginForm.fill(("","",Some(location.head))).copy(errors = Nil)))
    }
  }

  def authenticate = Action { implicit req =>
    setUser(None)
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
            Redirect(app.routes.Resources.index).withSession(u.toSeq:_*)
        }
      }
    )
  }

  def logout = SecureAction { implicit req =>
    setUser(None)
    Redirect(routes.Application.login).withNewSession.flashing(
      "success" -> "You have been logged out"
    )
  }(SecuritySpec(true))

}
