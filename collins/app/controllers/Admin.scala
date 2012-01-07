package controllers

import util.{Cache, SecuritySpec}
import views._

import play.api.Play
import play.api.db._
import play.api.db.evolutions._

object Admin extends SecureWebController {

  implicit val spec = SecuritySpec(true, Seq("infra"))

  def stats = SecureAction { implicit req =>
    Ok(html.admin.stats(Cache.stats()))
  }

  def evolve = SecureAction { implicit req =>
    val capp = Play.current
    try {
      OfflineEvolutions.applyScript(capp.path, capp.classloader, "collins")
    } catch {
      case e =>
    }
    Redirect(app.routes.Resources.index).flashing("success" -> "Evolution applied")
  }
}
