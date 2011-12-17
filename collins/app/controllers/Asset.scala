package controllers

import models.{Asset, LshwHelper}
import util.SecuritySpec
import views._

import play.api.mvc._

trait AssetController extends Controller {
  this: SecureController =>

  def show(id: Long) = SecureAction { implicit req =>
    Asset.findById(id) match {
      case None => Redirect(app.routes.Resources.index).flashing(
        "message" -> ("Could not find asset with id " + id.toString)
      )
      case Some(asset) =>
        val (lshwRep, mv) = LshwHelper.reconstruct(asset)
        Ok(html.asset.show(asset, lshwRep, mv))
    }
  }(SecuritySpec(true, Nil))

}
