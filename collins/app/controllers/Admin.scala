package controllers

import util.{SecuritySpec, Stats}
import util.plugins.Cache
import views._

import play.api.Play
import play.api.db._

object Admin extends SecureWebController {

  implicit val spec = SecuritySpec(true, Seq("infra"))

  def stats = SecureAction { implicit req =>
    Ok(html.admin.stats(Cache.stats(), Stats.get()))
  }

  def logs = SecureAction { implicit req =>
    Ok(html.admin.logs())
  }

  def clearCache = SecureAction { implicit req =>
    Cache.clear()
    Ok("ok")
  }

}
