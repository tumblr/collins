package collins.controllers.actions.asset

import scala.concurrent.Future

import play.api.data.Form
import play.api.data.Forms.of
import play.api.data.Forms.text
import play.api.data.Forms.tuple
import play.api.libs.concurrent.Execution.Implicits.defaultContext

import collins.controllers.Api
import collins.controllers.SecureController
import collins.controllers.actions.AssetAction
import collins.controllers.actions.RequestDataHolder
import collins.controllers.actions.SecureAction
import collins.controllers.forms.stateFormat
import collins.controllers.forms.statusFormat
import collins.models.State
import collins.models.{Status => AssetStatus}
import collins.models.AssetLifecycle
import collins.util.MessageHelper
import collins.util.views.Maintenance
import collins.util.security.SecuritySpecification
import collins.validation.StringUtil

object UpdateForMaintenance {
  object Messages extends MessageHelper("controllers.updateForMaintenance") {
    def missingDescriptionAndStatus = messageWithDefault("missingDescriptionAndStatus", "A description and status must be specified")
    def missingStatus = messageWithDefault("missingStatus", "Asset status must be specified")
    def missingState = messageWithDefault("missingState", "Asset state must be specified")
    def missingDescription = messageWithDefault("missingDescription", "A problem description must be specified")
  }
}

case class UpdateForMaintenanceAction(
  assetTag: String,
  spec: SecuritySpecification,
  handler: SecureController
) extends SecureAction(spec, handler) with AssetAction {

  import UpdateForMaintenance.Messages._

  case class ActionDataHolder(aStatus: AssetStatus, description: String, state: State) extends RequestDataHolder {
    def assetStatusName: String = aStatus.name
  }

  lazy val params: Either[String,ActionDataHolder] = Form(tuple(
    "status" -> of[AssetStatus],
    "description" -> text(1),
    "state"  -> of[State]
  )).bindFromRequest()(request).fold(
    err => {
      err.error("status").map { e =>
        Left(missingStatus)
      }.orElse {
        err.error("description").map { e =>
          Left(missingDescription)
        }
      }.orElse {
        err.error("state").map { e =>
          Left(missingState)
        }
      }.getOrElse {
        Left(missingDescriptionAndStatus)
      }
    },
    suc => {
      val status = suc._1
      val description = StringUtil.trim(suc._2)
      val state = suc._3
      description match {
        case None =>
          Left(missingDescription)
        case Some(r) =>
          Right(ActionDataHolder(status, r, state))
      }
    }
  )

  override def validate(): Either[RequestDataHolder,RequestDataHolder] = {
    withValidAsset(assetTag) { asset =>
      params.left.map(e => RequestDataHolder.error400(e))
    }
  }

  override def execute(rd: RequestDataHolder) = Future {
    rd match {
      case adh@ActionDataHolder(status, description, state) =>
        val lifeCycle = new AssetLifecycle(userOption, tattler)
        val success = if (status.id == AssetStatus.Maintenance.get.id) {
          Maintenance.toMaintenance(definedAsset, description, state, lifeCycle)
        } else {
          Maintenance.fromMaintenance(definedAsset, description, status.name, state, lifeCycle)
        }
        success match {
          case true => Api.statusResponse(true)
          case false => Api.errorResponse("Failed setting status to %s".format(adh.assetStatusName))
        }
    }
  }

}

