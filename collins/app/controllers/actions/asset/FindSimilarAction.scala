package controllers
package actions
package asset

import models.{Asset, AssetFinder, AssetView, Page, PageParams, RemoteCollinsHost}
import util.SecuritySpecification
import views.html
import play.api.mvc.Result
import models.Status.{Enum => AssetStatusEnum}

case class FindSimilarAction(
  assetTag: String,
  page: PageParams,
  spec: SecuritySpecification,
  handler: SecureController
) extends SecureAction(spec, handler) with AssetAction with AssetResultsAction{

  case class AssetDataHolder(asset: Asset) extends RequestDataHolder
  case class RedirectDataHolder(host: RemoteCollinsHost) extends RequestDataHolder

  override def validate(): Either[RequestDataHolder,RequestDataHolder] = assetFromTag(assetTag) match {
    case None => Left(assetNotFound(assetTag))
    case Some(asset) => Right(AssetDataHolder(asset))
  }


  override def execute(rd: RequestDataHolder) = rd match {
    case AssetDataHolder(asset) => {
      val finder = AssetFinder.Empty.copy(
        status = Some(AssetStatusEnum.Unallocated)
      )
      //TODO: Fix details to pull from query string
      handleSuccess(Asset.findSimilar(asset, page, finder), true)
    }
  }


  override protected def handleWebSuccess(p: Page[AssetView], details: Boolean): Result = {
    p.size match {
      case 0 =>
        Status.Redirect(app.routes.CookieApi.getAsset(assetTag)).flashing("message" -> AssetMessages.noMatch)
      case 1 =>
        Status.Redirect(p.items(0).remoteHost.getOrElse("") + app.routes.CookieApi.getAsset(p.items(0).tag))
      case n =>
        Status.Ok(views.html.asset.list(p)(flash, request))
    }
  }


}
