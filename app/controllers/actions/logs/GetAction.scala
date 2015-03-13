package controllers
package actions
package logs

import models.AssetLog
import models.conversions.AssetLogFormat
import util.security.SecuritySpecification
import play.api.libs.json._

case class GetAction(
  id: Int,
  spec: SecuritySpecification,
  handler: SecureController
) extends SecureAction(spec, handler) {

  case class ActionDataHolder(log_id: Int) extends RequestDataHolder

  override def validate(): Validation = {
    Right(ActionDataHolder(id))
  }

  override def execute(rdh: RequestDataHolder) = rdh match {
    case ActionDataHolder(log_id) =>
      AssetLog.findById(log_id) match {
        case Some(log) =>
          ResponseData(Status.Ok, Seq("SUCCESS" -> JsBoolean(true)) ++ Seq("Data" -> log.toJsValue()))
        case default =>
          Api.statusResponse(false, Status.NotFound)
      }
  }
}
