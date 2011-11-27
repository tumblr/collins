package controllers

import play.api._
import play.api.mvc._
import views._
import util.SecuredWebController

object Resources extends SecuredWebController {

  def index = Action { implicit req =>
    Ok(html.resources.index("Resources main page"))
  }

}
