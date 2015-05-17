package collins.controllers.actions.assettype

import scala.concurrent.Future

import play.api.libs.json.JsNumber
import play.api.libs.json.JsObject
import play.api.libs.concurrent.Execution.Implicits.defaultContext

import collins.controllers.Api
import collins.controllers.ResponseData
import collins.controllers.SecureController
import collins.controllers.actions.RequestDataHolder
import collins.controllers.actions.SecureAction
import collins.models.AssetType
import collins.util.MessageHelper
import collins.util.security.SecuritySpecification
import collins.validation.StringUtil

object DeleteAction {
  object Messages extends MessageHelper("controllers.AssetTypeApi.deleteAssetType") {
    def invalidName = messageWithDefault("invalidName", "The specified name is invalid")
    def noSuchName = messageWithDefault("noSuchName", "The specified name does not exist")
    def systemName = messageWithDefault("systemName",
      "The specified name is reserved and can not be deleted")
  }
}

/**
 * @include DeleteAction.desc
 *
 * Delete an asset type
 *
 * @apigroup AssetType
 * @apimethod DELETE
 * @apiurl /api/assettype/:name
 * @apiparam name String The name of the asset type to delete
 * @apirespond 202 success - delete accepted
 * @apirespond 400 invalid input
 * @apirespond 404 invalid asset type name
 * @apirespond 409 system name can not be deleted
 * @apirespond 500 error deleting asset type
 * @apiperm controllers.AssetTypeApi.deleteAssetType
 * @collinsshell {{{
 *  collins-shell asset_type delete NAME
 * }}}
 * @curlexample {{{
 *  curl -v -u blake:admin:first --basic \
 *    -X DELETE \
 *    http://localhost:9000/api/assettype/SERVICE
 * }}}
 */
case class DeleteAction(
  name: String,
  spec: SecuritySpecification,
  handler: SecureController
) extends SecureAction(spec, handler) {

  import DeleteAction.Messages._

  case class ActionDataHolder(atype: AssetType) extends RequestDataHolder

  override def validate(): Validation = {
    StringUtil.trim(name).filter(s => s.size > 1 && s.size <= 32).map(_.toUpperCase) match {
      case None => Left(RequestDataHolder.error400(invalidName))
      case Some(vname) => AssetType.findByName(vname) match {
        case None =>
          Left(RequestDataHolder.error404(noSuchName))
        case Some(atype) => AssetType.isSystemType(atype) match {
          case true =>
            Left(RequestDataHolder.error409(systemName))
          case false =>
            Right(ActionDataHolder(atype))
        }
      }
    }
  }

  override def execute(rdh: RequestDataHolder) = Future {
    rdh match {
      case ActionDataHolder(atype) => try {
        AssetType.delete(atype)
        ResponseData(Status.Accepted, JsObject(Seq("DELETED" -> JsNumber(1))))
      } catch {
        case e: Throwable =>
          Api.errorResponse(
            "Failed to delete asset type %s".format(atype.name),
            Status.InternalServerError,
            Some(e)
          )
      }
    }
  }
}
