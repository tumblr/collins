package controllers
package actions
package asset

import models.{Asset, AssetFinder, AssetType, AssetView, Page, PageParams, RemoteCollinsHost, SortDirection, Truthy}
import util.SecuritySpecification
import views.html
import play.api.mvc.Result
import models.Status.{Enum => AssetStatusEnum}

case class FindSimilarAction(
  assetTag: String,
  page: PageParams,
  details: String,
  spec: SecuritySpecification,
  handler: SecureController
) extends SecureAction(spec, handler) with AssetAction with AssetResultsAction{

  case class SimilarDataHolder(asset: Asset, details: Truthy) extends RequestDataHolder

  override def validate(): Either[RequestDataHolder,RequestDataHolder] = assetFromTag(assetTag) match {
    case None => Left(assetNotFound(assetTag))
    case Some(asset) => try Right(SimilarDataHolder(asset, new Truthy(details))) catch {
      case t: Truthy#TruthyException => Right(RequestDataHolder.error400(t.getMessage))
      case other => Right(RequestDataHolder.error500(other.getMessage))
    }
  }


  override def execute(rd: RequestDataHolder) = rd match {
    case SimilarDataHolder(asset, details) => {
      val finder = AssetFinder.Empty.copy(
        status = Some(AssetStatusEnum.Unallocated),
        assetType = Some(AssetType.Enum.ServerNode)
      )
      //TODO: Fix details to pull from query string
      handleSuccess(Asset.findSimilar(asset, page, finder),details.isTruthy)
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
