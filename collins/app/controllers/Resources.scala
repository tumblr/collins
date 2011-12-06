package controllers

import play.api._
import play.api.mvc._
import views._
import util.SecuritySpec

object Resources extends SecureWebController {

  implicit val spec = SecuritySpec(isSecure = true, Nil)

  def index = SecureAction { implicit req =>
    Ok(html.resources.index("Resources main page"))
  }

}
