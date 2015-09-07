package collins.controllers.actions.state

import scala.concurrent.Future

import play.api.libs.concurrent.Execution.Implicits.defaultContext
import play.api.libs.json.Json

import collins.controllers.ResponseData
import collins.controllers.SecureController
import collins.controllers.actions.RequestDataHolder
import collins.controllers.actions.SecureAction
import collins.models.State
import collins.models.conversions.StateFormat
import collins.util.MessageHelper
import collins.util.security.SecuritySpecification
import collins.validation.StringUtil

object GetAction {
  object Messages extends MessageHelper("controllers.AssetStateApi.getState") {
    def noSuchName = messageWithDefault("noSuchName", "The specified state does not exist")
  }
}

/**
 * Get an asset by name
 *
 * @apigroup AssetState
 * @apimethod GET
 * @apiurl /api/state/:name
 * @apiurl /api/states
 * @apiparam name String The name of the state to delete
 * @apirespond 200 success - the state
 * @apirespond 404 invalid state name
 * @apiperm controllers.AssetStateApi.getState
 * @collinsshell {{{
 *  collins-shell state get NAME
 *  collins-shell state list
 * }}}
 * @curlexample {{{
 *  curl -v -u blake:admin:first --basic http://localhost:9000/api/state/TESTING
 *  curl -v -u blake:admin:first --basic http://localhost:9000/api/states
 * }}}
 */
case class GetAction(
  name: Option[String],
  spec: SecuritySpecification,
  handler: SecureController
) extends SecureAction(spec, handler) {

  import GetAction.Messages._

  case class ActionDataHolder(state: Option[State]) extends RequestDataHolder

  override def validate(): Validation = name.map { name =>
    StringUtil.trim(name).filter(s => s.size > 1 && s.size <= 32) match {
      case None => Left(RequestDataHolder.error404(noSuchName))
      case Some(vname) => State.findByName(vname) match {
        case None =>
          Left(RequestDataHolder.error404(noSuchName))
        case Some(state) =>
          Right(ActionDataHolder(Some(state)))
      }
    }
  }.getOrElse {
    Right(ActionDataHolder(None))
  }

  override def execute(rdh: RequestDataHolder) = Future {
    rdh match {
      case ActionDataHolder(state) => state match {
        case None =>
          ResponseData(Status.Ok, Json.toJson(State.find()))
        case Some(state) =>
          ResponseData(Status.Ok, Json.toJson(state))
      }
    }
  }
}
