package controllers

import util.{AppConfig, SecuritySpec, Stats}
import util.plugins.Cache
import views._

import play.api.Play
import play.api.db._

object Admin extends SecureWebController {

  def stats = SecureAction { implicit req =>
    Ok(html.admin.stats(Cache.stats(), Stats.get()))
  }(Permissions.Admin.Stats)

  def logs = SecureAction { implicit req =>
    Ok(html.admin.logs())
  }(Permissions.AssetLogApi.GetAll)

  def clearCache = SecureAction { implicit req =>
    Cache.clear()
    Ok("ok")
  }(Permissions.Admin.ClearCache)

}
