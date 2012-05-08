package controllers
package actions
package asset

import models.Asset
import util.{OutputType, SecuritySpecification}
import views.html
import play.api.mvc.{Result, Results}

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

  override def handleError(rd: RequestDataHolder) = {
    if (OutputType.isHtml(request)) {
      val msg = rd.error.getOrElse(AssetMessages.notFound(assetTag))
      Redirect(app.routes.Resources.index).flashing("message" -> msg)
    } else {
      super.handleError(rd)
    }
  }

  protected def handleSuccess(asset: Asset): Result = {
    implicit val r = implicitRequest
    implicit val f = implicitFlash
    val display = asset.getAllAttributes.exposeCredentials(user.canSeePasswords)
    OutputType.isHtml(request) match {
      case true =>
        Results.Ok(html.asset.show(display, user))
      case false =>
        ResponseData(Results.Ok, display.toJsonObject).asResult
    }
  }

}

