package controllers

import models.Truthy

import util.Stats
import util.plugins.Cache
import util.plugins.solr.Solr
import util.security.AuthenticationProviderConfig
import views._

import play.api.Play
import play.api.db._

trait AdminApi {
  this: Api with SecureController =>
  
  /**
   * Force reindexing Solr
   *
   * waitForCompletion, if truthy, the response is synchronous, otherwise it returns immediately
   */
  def repopulateSolr(waitForCompletion: String) = SecureAction { implicit req =>
    Solr.populate().map{future => 
      if ((new Truthy(waitForCompletion)).isTruthy) Async {
        future.map{ _ => Ok("ok")}
      }
      else Ok("ok(async)")
    }.getOrElse(Ok("solr not enabled"))
  }(Permissions.Admin.ClearCache)

}
