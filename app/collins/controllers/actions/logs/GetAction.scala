package collins.controllers.actions.logs

import scala.concurrent.Future

import play.api.libs.json.JsBoolean
import play.api.libs.concurrent.Execution.Implicits.defaultContext

import collins.controllers.Api
import collins.controllers.ResponseData
import collins.controllers.SecureController
import collins.controllers.actions.RequestDataHolder
import collins.controllers.actions.SecureAction
import collins.models.AssetLog
import collins.util.security.SecuritySpecification

case class GetAction(
  id: Int,
  spec: SecuritySpecification,
  handler: SecureController
) extends SecureAction(spec, handler) {

  case class ActionDataHolder(log_id: Int) extends RequestDataHolder

  override def validate(): Validation = {
    Right(ActionDataHolder(id))
  }

  override def execute(rdh: RequestDataHolder) = Future { 
    rdh match {
      case ActionDataHolder(log_id) =>
        AssetLog.findById(log_id) match {
          case Some(log) =>
            ResponseData(Status.Ok, Seq("SUCCESS" -> JsBoolean(true), "Data" -> log.toJsValue()))
          case default =>
            Api.statusResponse(false, Status.NotFound)
        }
    }
  }
}
