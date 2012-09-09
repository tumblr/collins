package controllers
package actions
package asset

import models.{Asset, Status => AssetStatus}
import util.MessageHelper
import util.security.SecuritySpecification
import util.plugins.Maintenance
import validators.StringUtil

import play.api.data.Form
import play.api.data.Forms._

object UpdateForMaintenance {
  object Messages extends MessageHelper("controllers.updateForMaintenance") {
    def missingReasonAndStatus = messageWithDefault("missingReasonAndStatus", "A reason and status must be specified")
    def missingStatus = messageWithDefault("missingStatus", "Asset status must be specified")
    def missingReason = messageWithDefault("missingReason", "A reason must be specified")
  }
}

case class UpdateForMaintenanceAction(
  assetTag: String,
  spec: SecuritySpecification,
  handler: SecureController
) extends SecureAction(spec, handler) with AssetAction {

  import UpdateForMaintenance.Messages._

  case class ActionDataHolder(statusString: String, reason: String) extends RequestDataHolder {
    lazy val assetStatus: Option[AssetStatus] = AssetStatus.findByName(statusString)
    lazy val isError = !assetStatus.isDefined
    lazy val assetStatusName: String = assetStatus.map(_.name).getOrElse("Unknown")
  }

  lazy val params: Either[String,ActionDataHolder] = Form(tuple(
    "status" -> text(1),
    "reason" -> text(1)
  )).bindFromRequest()(request).fold(
    err => {
      err.error("status").map { e =>
        Left(missingStatus)
      }.orElse {
        err.error("reason").map { e =>
          Left(missingReason)
        }
      }.getOrElse {
        Left(missingReasonAndStatus)
      }
    },
    suc => {
      val status = StringUtil.trim(suc._1)
      val reason = StringUtil.trim(suc._2)
      (status, reason) match {
        case (None, None) =>
          Left(missingReasonAndStatus)
        case (None, Some(x)) =>
          Left(missingStatus)
        case (Some(x), None) =>
          Left(missingReason)
        case (Some(x), Some(y)) =>
          Right(ActionDataHolder(x, y))
      }
    }
  )

  override def validate(): Either[RequestDataHolder,RequestDataHolder] = {
    withValidAsset(assetTag) { asset =>
      params.left.map(e => RequestDataHolder.error400(e))
            .right.flatMap { s =>
              if (s.isError) {
                Left(RequestDataHolder.error400(missingStatus))
              } else {
                Right(s)
              }
            }
    }
  }

  override def execute(rd: RequestDataHolder) = rd match {
    case adh@ActionDataHolder(status, reason) =>
      val success = if (adh.assetStatus.get.id == AssetStatus.Enum.Maintenance.id) {
        Maintenance.toMaintenance(definedAsset, reason)
      } else {
        Maintenance.fromMaintenance(definedAsset, reason, status)
      }
      success match {
        case true => Api.statusResponse(true)
        case false => Api.errorResponse("Failed setting status to %s".format(adh.assetStatusName))
      }
  }

}

