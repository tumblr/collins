package collins.controllers.actions.asset

import scala.concurrent.Future

import play.api.data.Form
import play.api.data.Forms.optional
import play.api.data.Forms.text
import play.api.libs.concurrent.Execution.Implicits.defaultContext

import collins.controllers.Api
import collins.controllers.SecureController
import collins.controllers.actions.AssetAction
import collins.controllers.actions.RequestDataHolder
import collins.controllers.actions.SecureAction
import collins.models.AssetLifecycle
import collins.models.asset.AssetDeleter
import collins.util.security.SecuritySpecification
import collins.validation.StringUtil

case class DeleteAction(
  _assetTag: String,
  realDelete: Boolean,
  spec: SecuritySpecification,
  handler: SecureController
) extends SecureAction(spec, handler) with AssetAction {

  case class ActionDataHolder(options: Map[String,String]) extends RequestDataHolder

  lazy val reason: Option[String] = Form(
    "reason" -> optional(text(1))
  ).bindFromRequest()(request).fold(
    err => None,
    str => str.flatMap(StringUtil.trim(_))
  )

  override def validate(): Either[RequestDataHolder,RequestDataHolder] = {
    withValidAsset(_assetTag) { asset =>
      if (realDelete && !reason.isDefined) {
        Left(RequestDataHolder.error400("reason must be specified"))
      } else {
        val options = reason.map(r => Map("reason" -> r)).getOrElse(Map.empty)
        Right(ActionDataHolder(options))
      }
    }
  }

  override def execute(rd: RequestDataHolder) = Future { rd match {
    case ActionDataHolder(options) =>
      AssetLifecycle.decommissionAsset(definedAsset, options) match {
        case Left(throwable) =>
          handleError(
            RequestDataHolder.error409("Illegal state transition: %s".format(throwable.getMessage))
          )
        case Right(status) =>
          if (realDelete) {
            val errMsg = "User deleted asset %s. Reason: %s".format(
              definedAsset.tag, options.get("reason").getOrElse("Unspecified")
            )
            tattler.error(errMsg, definedAsset)
            Api.statusResponse(AssetDeleter.purge(definedAsset))
          } else {
            Api.statusResponse(status)
          }
      }
    }
  }

}

