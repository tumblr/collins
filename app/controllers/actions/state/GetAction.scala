package controllers
package actions
package state

import validators.StringUtil
import models.State
import util.MessageHelper
import util.security.SecuritySpecification
import play.api.libs.json.Json

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
 * @apiparam name String The name of the state to delete
 * @apirespond 200 success - the state
 * @apirespond 404 invalid state name
 * @apiperm controllers.AssetStateApi.getState
 * @collinsshell {{{
 *  collins-shell state get NAME
 * }}}
 * @curlexample {{{
 *  curl -v -u blake:admin:first --basic http://localhost:9000/api/state/TESTING
 * }}}
 */
case class GetAction(
  name: String,
  spec: SecuritySpecification,
  handler: SecureController
) extends SecureAction(spec, handler) {

  import GetAction.Messages._

  case class ActionDataHolder(state: State) extends RequestDataHolder

  override def validate(): Validation =
    StringUtil.trim(name).filter(s => s.size > 1 && s.size <= 32) match {
      case None => Left(RequestDataHolder.error404(noSuchName))
      case Some(vname) => State.findByName(vname) match {
        case None =>
          Left(RequestDataHolder.error404(noSuchName))
        case Some(state) =>
          Right(ActionDataHolder(state))
      }
    }

  override def execute(rdh: RequestDataHolder) = rdh match {
    case ActionDataHolder(state) =>
      ResponseData(Status.Ok, Json.toJson(state))
  }
}
