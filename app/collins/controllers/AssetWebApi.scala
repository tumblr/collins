package collins.controllers

import play.api.libs.json.JsNumber
import play.api.libs.json.JsObject
import play.api.mvc.Action
import play.api.mvc.Results

import collins.controllers.actions.asset.DeleteAction
import collins.controllers.actors.AssetCancelProcessor
import collins.models.Asset
import collins.util.UserTattler
import collins.util.concurrent.BackgroundProcessor
import collins.util.concurrent.BackgroundProcessor.SendType
import collins.util.config.AppConfig

trait AssetWebApi {
  this: Api with SecureController =>

  def realDeleteAsset(tag: String) =
    DeleteAction(tag, true, Permissions.AssetWebApi.DeleteAsset, this)

  // POST /asset/:tag/cancel
  def cancelAsset(tag: String) = Authenticated { user => 
    if (AppConfig.ignoreAsset(tag)) {
      Action { implicit req => formatResponseData(
        Api.getErrorMessage("Specified asset has been configured to not permit this operation")
      ) }
    } else {
      Action.async { implicit req => val asset = Asset.findByTag(tag)
        val processor = (r: SendType[Either[ResponseData,Long]]) => r match {
            case Left(ex) => Api.getErrorMessage(ex.getMessage).asResult
            case Right(res) => res match {
              case Left(err) => err.asResult
              case Right(success) =>
                UserTattler.notice(asset.get, user, "Server cancelled")
                ResponseData(Results.Ok, JsObject(Seq("SUCCESS" -> JsNumber(success)))).asResult
            }
        }
        BackgroundProcessor.send(AssetCancelProcessor(tag)) { processor(_) }
    }
    }}(Permissions.AssetWebApi.CancelAsset)

}
