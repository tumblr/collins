package controllers

import models.Truthy

import util.Stats
import util.plugins.Cache
import collins.solr.Solr
import util.security.AuthenticationProviderConfig
import views._

import play.api.Play
import play.api.db._
import play.api.libs.json._
import play.api.mvc._

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
        future.map{ _ => Ok(ApiResponse.formatJsonMessage(Results.Ok, JsString("ok")))}
      }
      else Ok("ok(async)")
    }.getOrElse(Results.NotImplemented(ApiResponse.formatJsonError("Solr plugin not enabled!", None)))
  }(Permissions.Admin.ClearCache)

}
