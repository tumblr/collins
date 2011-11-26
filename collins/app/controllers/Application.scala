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
        case(username,password) => username == "blake" && password == "admin"
      })
  )

  def isLoggedIn(implicit request: RequestHeader) = session.get(Security.USERNAME).isDefined
  def username(implicit request: RequestHeader) = session.get(Security.USERNAME).getOrElse("")

  def home = show("index")
  def show(page: String) = Action { implicit req =>
    page match {
      case "index" =>
        isLoggedIn match {
          case true => Ok(html.index("Welcome, " + username))
          case false => Ok(html.index("Home Page/Index View"))
        }
      case _ =>
        NotFound(html.index("404 Page"))
    }
  }


  def login = Action { implicit req =>
    Ok(html.login(loginForm))
  }

  def authenticate = Action { implicit req =>
    loginForm.bindFromRequest.fold(
      formWithErrors =>
        BadRequest(html.login(
          formWithErrors.bind(formWithErrors.data - "password")
        )),
      user => Redirect(routes.Application.home).withSession(Security.USERNAME -> user._1)
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
