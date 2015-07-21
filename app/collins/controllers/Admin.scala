package collins.controllers

import collins.solr.Solr
import collins.util.Stats

import views.html

object Admin extends SecureWebController {

  def stats = SecureAction { implicit req =>
    Ok(html.admin.stats(Stats.get()))
  }(Permissions.Admin.Stats)

  def logs = SecureAction { implicit req =>
    Ok(html.admin.logs())
  }(Permissions.AssetLogApi.GetAll)

  def populateSolr = SecureAction {implicit req => 
    Solr.populate()
    Redirect(collins.app.routes.Resources.index).flashing("error" -> "Repopulating Solr index in the background.  May take a few minutes to complete")
  }(Permissions.Admin.ClearCache)

}
