package controllers
package actions
package asset

import forms._

import models.{Asset, AssetFinder, AssetType, AssetView, Page, PageParams, RemoteCollinsHost, SortDirection, Truthy}
import models.Status.{Enum => AssetStatusEnum}
import models.SortType._
import models.SortDirection._

import play.api.mvc.Result
import play.api.data.Form
import play.api.data.Forms._
import play.api.Logger
import play.api.mvc.{AnyContent, Request, Result}

import util.MessageHelper
import util.SecuritySpecification

import views.html


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
    sortType: Option[SortType]
  ) extends RequestDataHolder

  object SimilarDataHolder extends MessageHelper("similar"){
    def form = Form(tuple(
      "sortType"        -> optional(of[SortType]),
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
      case other => Right(RequestDataHolder.error500(other.getMessage))
    }
  }


  override def execute(rd: RequestDataHolder) = rd match {
    case SimilarDataHolder(asset, details, only, sortType) => {
      Logger.logger.debug(only.toString)
      val finder = AssetFinder.empty.copy(
        status = if(only.map{_.isTruthy}.getOrElse(true)) Some(AssetStatusEnum.Unallocated) else None,
        assetType = Some(AssetType.Enum.ServerNode)
      )
      Logger.logger.debug(finder.status.toString)
      handleSuccess(Asset.findSimilar(asset, page, finder, sortType.getOrElse(Distribution)),details.map{_.isTruthy}.getOrElse(false))
    }
  }


  override protected def handleWebSuccess(p: Page[AssetView], details: Boolean): Result = {
    p.size match {
      case 0 =>
        Status.Redirect(app.routes.CookieApi.getAsset(assetTag)).flashing("message" -> AssetMessages.noMatch)
      case 1 =>
        Status.Redirect(p.items(0).remoteHost.getOrElse("") + app.routes.CookieApi.getAsset(p.items(0).tag))
      case n =>
        Status.Ok(views.html.asset.list(p, page.sort, None, Some((newPage: Int) => app.routes.Resources.similar(assetTag, newPage, 50).toString))(flash, request))
    }
  }


}
