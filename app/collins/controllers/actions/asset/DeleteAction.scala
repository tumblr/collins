package collins.controllers.actions.asset

import scala.concurrent.Future

import play.api.data.Form
import play.api.data.Forms.optional
import play.api.data.Forms.text
import play.api.data.Forms.tuple
import play.api.data.Forms.nonEmptyText
import play.api.data.Forms.boolean
import play.api.data.Forms.default
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
  nuke: Boolean,
  spec: SecuritySpecification,
  handler: SecureController
) extends SecureAction(spec, handler) with AssetAction {

  case class ActionDataHolder(reason: String, nuke: Boolean) extends RequestDataHolder

  val dataForm = Form(tuple(
    "reason" -> nonEmptyText,
    "nuke" -> default(boolean, false)
  ))

  override def validate(): Either[RequestDataHolder,RequestDataHolder] = {
    withValidAsset(_assetTag) { asset =>
      dataForm.bindFromRequest()(request).fold(
        err => Left(RequestDataHolder.error400("Reason must be specified.")),
        form => {
        val (reason, nuke) = form
        val options = reason.map(r => Map("reason" -> r, "nuke" -> nuke));
        Right(ActionDataHolder(reason, nuke))
    })}
  }

  override def execute(rd: RequestDataHolder) = Future { rd match {
    case ActionDataHolder(reason, nuke) =>
      val lifeCycle = new AssetLifecycle(userOption(), tattler)
      lifeCycle.decommissionAsset(definedAsset, reason, nuke) match {
        case Left(throwable) =>
          handleError(
            RequestDataHolder.error409("Illegal state transition: %s".format(throwable.getMessage))
          )
        case Right(status) =>
          if (nuke) {
            val errMsg = "User deleted asset %s. Reason: %s".format(
              definedAsset.tag, reason
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

