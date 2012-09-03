package controllers

import util.Stats
import util.plugins.Cache
import util.plugins.solr.Solr
import util.security.AuthenticationProviderConfig
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

  def populateSolr = SecureAction {implicit req => 
    Solr.populate()
    Redirect(app.routes.Resources.index).flashing("error" -> "Repopulating Solr index in the background.  May take a few minutes to complete")
  }(Permissions.Admin.ClearCache)

}
