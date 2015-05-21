package collins.controllers

import play.api.libs.json.JsNumber
import play.api.libs.json.JsObject
import play.api.mvc.Action
import play.api.mvc.AsyncResult
import play.api.mvc.Results

import collins.controllers.actions.asset.DeleteAction
import collins.controllers.actors.AssetCancelProcessor
import collins.models.Asset
import collins.util.UserTattler
import collins.util.concurrent.BackgroundProcessor
import collins.util.config.AppConfig

trait AssetWebApi {
  this: Api with SecureController =>

  def realDeleteAsset(tag: String) =
    DeleteAction(tag, true, Permissions.AssetWebApi.DeleteAsset, this)

  // POST /asset/:tag/cancel
  def cancelAsset(tag: String) = Authenticated { user => Action { implicit req =>
    if (AppConfig.ignoreAsset(tag)) {
      formatResponseData(
        Api.getErrorMessage("Specified asset has been configured to not permit this operation")
      )
    } else {
      val asset = Asset.findByTag(tag)
      AsyncResult {
        BackgroundProcessor.send(AssetCancelProcessor(tag)) { case(ex,res) =>
          val rd: ResponseData = ex.map { err =>
            Api.getErrorMessage(err.getMessage)
          }.orElse{
            res.get match {
              case Left(err) => Some(err)
              case Right(success) =>
                UserTattler.notice(asset.get, user, "Server cancelled")
                Some(ResponseData(Results.Ok, JsObject(Seq("SUCCESS" -> JsNumber(success)))))
            }
          }.get
          formatResponseData(rd)
        }
      }
    }
  }}(Permissions.AssetWebApi.CancelAsset)

}
