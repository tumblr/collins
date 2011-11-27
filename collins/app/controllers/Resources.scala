package controllers

import play.api._
import play.api.mvc._
import views._
import util.Secured

object Resources extends Controller with Secured {

  def index = Action { implicit req =>
    Ok(html.resources.index("Resources main page"))
  }

}
