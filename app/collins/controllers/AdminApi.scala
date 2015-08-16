package collins.controllers

import play.api.libs.concurrent.Execution.Implicits.defaultContext
import play.api.libs.json.JsString
import play.api.mvc.Results

import collins.models.Asset
import collins.models.Truthy
import collins.solr.Solr

trait AdminApi {
  this: Api with SecureController =>

  /**
   * Force reindexing Solr
   *
   * waitForCompletion, if truthy, the response is synchronous, otherwise it returns immediately
   */
  def repopulateSolr(waitForCompletion: String) = SecureAction { implicit req =>
    if ((new Truthy(waitForCompletion)).isTruthy) {
      Solr.populate().map { _ => Ok(ApiResponse.formatJsonMessage(Results.Ok, JsString("ok"))) }
        .getOrElse(Results.NotImplemented(ApiResponse.formatJsonError("Solr plugin not enabled!", None)))
    } else {
      Ok("ok(async)")
    }
  }(Permissions.Admin.ClearCache)

  def reindexAsset(tag: String) = SecureAction { implicit req =>
    Asset.findByTag(tag).map{asset =>
      Solr.updateAssets(List(asset))
      Ok(ApiResponse.formatJsonMessage(Results.Ok, JsString("ok")))
    }.getOrElse(Results.BadRequest(ApiResponse.formatJsonError("Asset %s not found".format(tag), None)))
  }

}
