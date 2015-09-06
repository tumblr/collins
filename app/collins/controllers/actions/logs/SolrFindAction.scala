package collins.controllers.actions.logs

import scala.concurrent.Future

import play.api.libs.concurrent.Execution.Implicits.defaultContext
import play.api.libs.json.JsObject
import play.api.libs.json.Json

import collins.controllers.ResponseData
import collins.controllers.SecureController
import collins.controllers.actions.AssetAction
import collins.controllers.actions.RequestDataHolder
import collins.controllers.actions.SecureAction
import collins.models.AssetLog
import collins.models.conversions.AssetLogFormat
import collins.models.shared.Page
import collins.models.shared.PageParams
import collins.solr.AssetLogDocType
import collins.solr.AssetLogSearchQuery
import collins.solr.CollinsQueryParser
import collins.solr.TypedSolrExpression
import collins.util.security.SecuritySpecification

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

  override def execute(rd: RequestDataHolder) = Future {
    rd match {
      case adh@ActionDataHolder(params, query) =>
        val logs = getLogs(adh)
        val pageMap = getPaginationMap(logs)
        ResponseData(Status.Ok, JsObject(pageMap ++ Seq(
          "Data" -> Json.toJson(logs.items)
        )), logs.getPaginationHeaders)
    }
  }

  protected def getLogs(adh: ActionDataHolder): Page[AssetLog] = {
    (new AssetLogSearchQuery(adh.query, adh.params)).getPage(AssetLog.findByIds(_)).fold(
      err => throw new Exception(err),
      res => res
    )
  }

  protected def getPaginationMap(logs: Page[AssetLog]) = logs.getPaginationJsObject

}
