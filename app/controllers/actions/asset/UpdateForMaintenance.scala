package controllers
package actions
package asset

import forms._
import models.{Asset, State, Status => AssetStatus}
import util.MessageHelper
import util.security.SecuritySpecification
import util.plugins.Maintenance
import validators.StringUtil

import play.api.data.Form
import play.api.data.Forms._

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

  override def execute(rd: RequestDataHolder) = rd match {
    case adh@ActionDataHolder(status, description, state) =>
      val success = if (status.id == AssetStatus.Maintenance.get.id) {
        Maintenance.toMaintenance(definedAsset, description, state)
      } else {
        Maintenance.fromMaintenance(definedAsset, description, status.name, state)
      }
      success match {
        case true => Api.statusResponse(true)
        case false => Api.errorResponse("Failed setting status to %s".format(adh.assetStatusName))
      }
  }

}

