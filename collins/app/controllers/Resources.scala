package controllers

import play.api._
import play.api.mvc._
import views._
import util.SecuritySpec
import models._

object Resources extends SecureWebController {

  implicit val spec = SecuritySpec(isSecure = true, Nil)

  def index = SecureAction { implicit req =>
    Ok(html.resources.index(AssetMeta.getAll()))
  }

  def find = SecureAction { implicit req =>
    Ok(html.resources.list(Asset.findByMeta(req.queryString)))
  }

}
