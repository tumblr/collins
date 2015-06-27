package collins.controllers.actions.asset

import scala.concurrent.Future

import play.api.data._
import play.api.data.Forms._
import play.api.data.Form
import play.api.data.Forms.optional
import play.api.data.Forms.text
import play.api.data.Forms.tuple
import play.api.libs.concurrent.Execution.Implicits.defaultContext

import collins.controllers.Api
import collins.controllers.SecureController
import collins.controllers.actions.AssetAction
import collins.controllers.actions.RequestDataHolder
import collins.controllers.actions.SecureAction
import collins.models.AssetLifecycle
import collins.models.asset.AssetDeleter
import collins.util.SystemTattler
import collins.util.security.SecuritySpecification
import collins.validation.StringUtil

case class DeleteAction(
  _assetTag: String,
  nuke: Boolean,
  spec: SecuritySpecification,
  handler: SecureController
) extends SecureAction(spec, handler) with AssetAction {

  case class ActionDataHolder(reason: String, nuke: Boolean) extends RequestDataHolder

  val dataForm = Form(tuple(
    "reason" -> optional(nonEmptyText),
    "nuke" -> default(boolean, false)
  ))

  override def validate(): Either[RequestDataHolder,RequestDataHolder] = {
    withValidAsset(_assetTag) { asset =>
      dataForm.bindFromRequest()(request).fold(
        err => Left(RequestDataHolder.error400("Invalid pool or count specified")),
        form => {
        val (reason, nuke) = form
      if (!reason.isDefined) {
        Left(RequestDataHolder.error400("reason must be specified"))
      } else {
        val options = reason.map(r => Map("reason" -> r, "nuke" -> nuke));
        Right(ActionDataHolder(options))
      }
    })}
  }

  override def execute(rd: RequestDataHolder) = Future { rd match {
    case ActionDataHolder(options) =>
      AssetLifecycle.decommissionAsset(definedAsset, options) match {
        case Left(throwable) =>
          handleError(
            RequestDataHolder.error409("Illegal state transition: %s".format(throwable.getMessage))
          )
        case Right(status) =>
          if (options.get("nuke")) {
            val errMsg = "User deleted asset %s. Reason: %s".format(
              definedAsset.tag, options.get("reason").getOrElse("Unspecified")
            )
            SystemTattler.safeError(errMsg)
            Api.statusResponse(AssetDeleter.purge(definedAsset))
          } else {
            Api.statusResponse(status)
          }
      }
    }
  }

}

