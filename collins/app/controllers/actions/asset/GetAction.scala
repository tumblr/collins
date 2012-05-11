package controllers
package actions
package asset

import models.Asset
import util.SecuritySpecification
import views.html
import play.api.mvc.Result

case class GetAction(
  assetTag: String,
  spec: SecuritySpecification,
  handler: SecureController
) extends SecureAction(spec, handler) with AssetAction {

  case class AssetDataHolder(asset: Asset) extends RequestDataHolder

  override def validate(): Either[RequestDataHolder,RequestDataHolder] = {
    assetFromTag(assetTag) match {
      case None => Left(assetNotFound(assetTag))
      case Some(asset) => Right(AssetDataHolder(asset))
    }
  }

  override def execute(rd: RequestDataHolder) = rd match {
    case AssetDataHolder(asset) => handleSuccess(asset)
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

