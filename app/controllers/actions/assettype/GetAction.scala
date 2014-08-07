package controllers
package actions
package assettype

import collins.validation.StringUtil
import models.AssetType
import util.MessageHelper
import util.security.SecuritySpecification
import play.api.libs.json.Json

object GetAction {
  object Messages extends MessageHelper("controllers.AssetTypeApi.getAssetType") {
    def noSuchName = messageWithDefault("noSuchName", "The specified asset type does not exist")
  }
}

/**
 * Get an asset type by name
 *
 * @apigroup AssetType
 * @apimethod GET
 * @apiurl /api/assettype/:name
 * @apiurl /api/assettypes
 * @apiparam name String The name of the asset type to get
 * @apirespond 200 success - the asset type
 * @apirespond 404 invalid asset type name
 * @apiperm controllers.AssetTypeApi.getAssetType
 * @curlexample {{{
 *  curl -v -u blake:admin:first --basic http://localhost:9000/api/assettype/SERVICE
 *  curl -v -u blake:admin:first --basic http://localhost:9000/api/assettypes
 * }}}
 */
case class GetAction(
  name: Option[String],
  spec: SecuritySpecification,
  handler: SecureController
) extends SecureAction(spec, handler) {

  import GetAction.Messages._

  case class ActionDataHolder(atype: Option[AssetType]) extends RequestDataHolder

  override def validate(): Validation = name.map { name =>
    StringUtil.trim(name).filter(s => s.size > 1 && s.size <= 32) match {
      case None => Left(RequestDataHolder.error404(noSuchName))
      case Some(vname) => AssetType.findByName(vname) match {
        case None =>
          Left(RequestDataHolder.error404(noSuchName))
        case Some(atype) =>
          Right(ActionDataHolder(Some(atype)))
      }
    }
  }.getOrElse {
    Right(ActionDataHolder(None))
  }

  override def execute(rdh: RequestDataHolder) = rdh match {
    case ActionDataHolder(atype) => atype match {
      case None =>
        ResponseData(Status.Ok, Json.toJson(AssetType.find()))
      case Some(atype) =>
        ResponseData(Status.Ok, Json.toJson(atype))
    }
  }
}
