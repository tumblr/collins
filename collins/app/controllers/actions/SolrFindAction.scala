package controllers
package actions
package resources

import asset.{AssetFinderDataHolder, FindAction => AssetFindAction}

import models.{Asset, AssetView, Page, PageParams, Truthy}
import util.SecuritySpecification
import util.plugins.solr._

import play.api.data.Form
import play.api.data.Forms._
import play.api.mvc.Result

case class SolrFindAction (
  pageParams: PageParams,
  query: String,
  details: Truthy,
  spec: SecuritySpecification,
  handler: SecureController
) extends AssetFindAction(pageParams, spec, handler) with AssetResultsAction {

  case class SolrQueryDataHolder(query: CollinsSearchQuery) extends RequestDataHolder

  override def validate(): Either[RequestDataHolder, RequestDataHolder] = (new CollinsQueryParser)
    .parseQuery(query)
    .right
    .flatMap{_.typeCheck}
    .fold[Either[RequestDataHolder, RequestDataHolder]](
      error => Left(RequestDataHolder.error400(error)),
      expr => Right(SolrQueryDataHolder(CollinsSearchQuery(expr, pageParams)))
    )

  override def execute(rd: RequestDataHolder) = rd match {
    case SolrQueryDataHolder(query) => {
      query.getPage().fold (
        error => handleError(RequestDataHolder.error500(error)),
        page => handleSuccess(page, details.isTruthy)
      )
    }
  }

  override protected def handleWebSuccess(p: Page[AssetView], details: Boolean): Result = {
    p.size match {
      case 0 =>
        Redirect(app.routes.Resources.index).flashing("message" -> AssetMessages.noMatch)
      case 1 =>
        Status.Redirect(p.items(0).remoteHost.getOrElse("") + app.routes.CookieApi.getAsset(p.items(0).tag))
      case n =>
        Status.Ok(views.html.asset.list(p)(flash, request))
    }
  }

  override def handleWebError(rd: RequestDataHolder): Option[Result] = Some(
    Redirect(app.routes.Resources.index).flashing("error" -> rd.toString)
  )

}
