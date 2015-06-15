package collins.controllers.actions.asset

import scala.concurrent.Future

import play.api.Logger
import play.api.data.Form
import play.api.data.Forms.of
import play.api.data.Forms.optional
import play.api.data.Forms.tuple
import play.api.mvc.AnyContent
import play.api.mvc.Request
import play.api.mvc.Result
import play.api.libs.concurrent.Execution.Implicits.defaultContext

import collins.controllers.SecureController
import collins.controllers.actions.AssetAction
import collins.controllers.actions.AssetResultsAction
import collins.controllers.actions.RequestDataHolder
import collins.controllers.actions.SecureAction
import collins.controllers.forms.sortTypeformat
import collins.controllers.forms.truthyFormat
import collins.models.Asset
import collins.models.AssetFinder
import collins.models.AssetSort
import collins.models.AssetType
import collins.models.{Status => AssetStatus}
import collins.models.Truthy
import collins.models.asset.AssetView
import collins.models.shared.Page
import collins.models.shared.PageParams
import collins.util.MessageHelper
import collins.util.security.SecuritySpecification

case class FindSimilarAction(
  assetTag: String,
  page: PageParams,
  spec: SecuritySpecification,
  handler: SecureController
) extends SecureAction(spec, handler) with AssetAction with AssetResultsAction{

  case class SimilarDataHolder(
    asset: Asset,
    details: Option[Truthy], 
    onlyUnallocated: Option[Truthy],
    sortType: Option[AssetSort.Type]
  ) extends RequestDataHolder

  object SimilarDataHolder extends MessageHelper("similar"){
    def form = Form(tuple(
      "sortType"        -> optional(of[AssetSort.Type]),
      "onlyUnallocated" -> optional(of[Truthy]),
      "details"         -> optional(of[Truthy])
    ))

    def processRequest(asset: Asset, request: Request[AnyContent]): Either[RequestDataHolder,SimilarDataHolder] = form.bindFromRequest()(request).fold(
      err => Left(RequestDataHolder.error400(fieldError(err))),
      succ => {
        val (sortType, onlyUnallocated, details) = succ
        Right(SimilarDataHolder(asset, details, onlyUnallocated, sortType))
      }
    )

    protected def fieldError(f: Form[_]) = f match {
      case e if e.error("sortType").isDefined => message("sorttype.invalid")
      case e if e.error("details").isDefined => rootMessage("error.truthy", "details")
      case e if e.error("onlyUnallocated").isDefined => rootMessage("error.truthy", "onlyUnallocated")
      case n => "Unexpected error occurred"
    }
  }

  override def validate(): Either[RequestDataHolder,RequestDataHolder] = assetFromTag(assetTag) match {
    case None => Left(assetNotFound(assetTag))
    case Some(asset) => try SimilarDataHolder.processRequest(asset, request()) catch {
      case other: Throwable => Right(RequestDataHolder.error500(other.getMessage))
    }
  }


  override def execute(rd: RequestDataHolder) = Future { 
    rd match {
      case SimilarDataHolder(asset, details, only, sortType) => {
        Logger.logger.debug(only.toString)
        val finder = AssetFinder.empty.copy(
          status = if(only.map{_.isTruthy}.getOrElse(true)) AssetStatus.Unallocated else None,
          assetType = AssetType.ServerNode
        )
        Logger.logger.debug(finder.status.toString)
        handleSuccess(Asset.findSimilar(asset, page, finder, sortType.getOrElse(AssetSort.Distribution)),details.map{_.isTruthy}.getOrElse(false))
      }
    }
  }


  override protected def handleWebSuccess(p: Page[AssetView], details: Boolean): Result = {
    p.size match {
      case 0 =>
        Status.Redirect(collins.app.routes.CookieApi.getAsset(assetTag)).flashing("message" -> AssetMessages.noMatch)
      case 1 =>
        Status.Redirect(p.items(0).remoteHost.getOrElse("") + collins.app.routes.CookieApi.getAsset(p.items(0).tag))
      case n =>
        Status.Ok(views.html.asset.list(p, page.sort, None, Some((newPage: Int) => collins.app.routes.Resources.similar(assetTag, newPage, 50).toString))(flash, request))
    }
  }


}
