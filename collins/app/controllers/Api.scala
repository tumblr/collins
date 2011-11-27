package controllers

import play.api._
import play.api.mvc._
import models.User
import util.SecuredApiController

object Api extends SecuredApiController {

  def rolePing = Authorized("admin") {
    Action { implicit req =>
      Ok("You have the right role!")
    }
  }

  def ping = Action { implicit req =>
    Ok("Authenticated!")
  }

}
