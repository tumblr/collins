package controllers
package actions
package state

import validators.StringUtil

import models.{Asset, State, Status => AStatus}
import util.MessageHelper
import util.security.SecuritySpecification

import play.api.data.Form
import play.api.data.Forms._
import play.api.libs.json.{JsNumber, JsObject}

object DeleteAction {
  object Messages extends MessageHelper("controllers.AssetStateApi.deleteState") {
    def invalidName = messageWithDefault("invalidName", "The specified name is invalid")
    def noSuchName = messageWithDefault("noSuchName", "The specified name does not exist")
    def systemName = messageWithDefault("systemName",
      "The specified name is reserved and can not be deleted")
  }
}

/**
 * @include DeleteAction.desc
 *
 * Delete an asset state
 *
 * @apigroup AssetState
 * @apimethod DELETE
 * @apiurl /api/state/:name
 * @apiparam name String The name of the state to delete
 * @apirespond 202 success - delete accepted
 * @apirespond 400 invalid input
 * @apirespond 404 invalid state name
 * @apirespond 409 system name can not be deleted
 * @apirespond 500 error deleting state
 * @apiperm controllers.AssetStateApi.deleteState
 * @collinsshell {{{
 *  collins-shell state delete NAME
 * }}}
 * @curlexample {{{
 *  curl -v -u blake:admin:first --basic \
 *    -X DELETE \
 *    http://localhost:9000/api/state/TESTING
 * }}}
 */
case class DeleteAction(
  name: String,
  spec: SecuritySpecification,
  handler: SecureController
) extends SecureAction(spec, handler) {

  import DeleteAction.Messages._

  case class ActionDataHolder(state: State) extends RequestDataHolder

  override def validate(): Validation = {
    StringUtil.trim(name).filter(s => s.size > 1 && s.size <= 32).map(_.toUpperCase) match {
      case None => Left(RequestDataHolder.error400(invalidName))
      case Some(vname) => State.findByName(vname) match {
        case None =>
          Left(RequestDataHolder.error404(noSuchName))
        case Some(state) => State.isSystemState(state) match {
          case true =>
            Left(RequestDataHolder.error409(systemName))
          case false =>
            Right(ActionDataHolder(state))
        }
      }
    }
  }

  override def execute(rdh: RequestDataHolder) = rdh match {
    case ActionDataHolder(state) => try {
      val deletes = Asset.resetState(state, 0)
      State.delete(state)
      ResponseData(Status.Accepted, JsObject(Seq("DELETED" -> JsNumber(deletes + 1))))
    } catch {
      case e =>
        Api.errorResponse(
          "Failed to delete state %s".format(state.name),
          Status.InternalServerError,
          Some(e)
        )
    }
  }
}
