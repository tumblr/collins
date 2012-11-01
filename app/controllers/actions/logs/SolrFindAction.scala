
package controllers
package actions
package logs

import collins.solr.{AssetLogDocType, TypedSolrExpression, CollinsQueryParser, AssetLogSearchQuery}

import models.{Asset, AssetLog, Page, PageParams}
import models.conversions.AssetLogFormat
import util.security.SecuritySpecification
import play.api.libs.json._

case class SolrFindAction(
  query: String,
  pageParams: PageParams,
  spec: SecuritySpecification,
  handler: SecureController
) extends SecureAction(spec, handler) with AssetAction {

  case class ActionDataHolder(params: PageParams, query: TypedSolrExpression) extends RequestDataHolder

  override def validate(): Validation = CollinsQueryParser(List(AssetLogDocType))
    .parseQuery(query)
    .right
    .flatMap(_.typeCheck)
    .fold(
      err => Left(RequestDataHolder.error400(err)),
      expr => Right(ActionDataHolder(pageParams, expr))
    )

  override def execute(rd: RequestDataHolder) = rd match {
    case adh@ActionDataHolder(params, query) =>
      val logs = getLogs(adh)
      val pageMap = getPaginationMap(logs)
      ResponseData(Status.Ok, JsObject(pageMap ++ Seq(
        "Data" -> Json.toJson(logs.items)
      )), logs.getPaginationHeaders)
  }

  protected def getLogs(adh: ActionDataHolder): Page[AssetLog] = {
    (new AssetLogSearchQuery(adh.query, adh.params)).getPage.fold(
      err => throw new Exception(err),
      res => res
    )
  }

  protected def getPaginationMap(logs: Page[AssetLog]) = logs.getPaginationJsObject

}
