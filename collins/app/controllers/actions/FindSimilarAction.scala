package controllers
package actions
package asset

import models.{Asset, RemoteCollinsHost}
import util.SecuritySpecification
import views.html
import play.api.mvc.Result

case class FindSimilarAction(
  assetTag: String,
  spec: SecuritySpecification,
  handler: SecureController
) extends SecureAction(spec, handler) with AssetAction {

  case class AssetDataHolder(asset: Asset) extends RequestDataHolder
  case class RedirectDataHolder(host: RemoteCollinsHost) extends RequestDataHolder

  override def validate(): Either[RequestDataHolder,RequestDataHolder] = assetFromTag(assetTag) match {
    case None => Left(assetNotFound(assetTag))
    case Some(asset) => Right(AssetDataHolder(asset))
  }


  override def execute(rd: RequestDataHolder) = rd match {
    case AssetDataHolder(asset) => handleSuccess(asset)
    case RedirectDataHolder(host) => isHtml match {
      case true =>
        Status.MovedPermanently(host.host + app.routes.CookieApi.getAsset(assetTag))
      case false =>
        Status.MovedPermanently(host.host + app.routes.Api.getAsset(assetTag, None))
    }
  }

  override def handleWebError(rd: RequestDataHolder) = {
    val msg = rd.error.getOrElse(AssetMessages.notFound(assetTag))
    Some(Redirect(app.routes.Resources.index).flashing("message" -> msg))
  }

  protected def handleSuccess(asset: Asset): Result = {
    val display = asset.getAllAttributes.exposeCredentials(user.canSeePasswords)
    isHtml match {
      case true =>
        Status.Ok(html.asset.show(display, user)(flash, request))
      case false =>
        ResponseData(Status.Ok, display.toJsonObject)
    }
  }

}
