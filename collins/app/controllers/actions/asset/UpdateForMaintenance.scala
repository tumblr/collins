package controllers
package actions
package asset

import models.{Asset, Status => AssetStatus}
import util.SecuritySpecification
import util.plugins.Maintenance
import validators.StringUtil

import play.api.data.Form
import play.api.data.Forms._

case class UpdateForMaintenanceAction(
  assetTag: String,
  spec: SecuritySpecification,
  handler: SecureController
) extends SecureAction(spec, handler) with AssetAction {

  case class ActionDataHolder(statusString: String, reason: String) extends RequestDataHolder {
    lazy val assetStatus: Option[AssetStatus] = AssetStatus.findByName(statusString)
    lazy val isError = !assetStatus.isDefined
    lazy val assetStatusName: String = assetStatus.map(_.name).getOrElse("Unknown")
  }

  lazy val params: Option[ActionDataHolder] = Form(tuple(
    "status" -> text(1),
    "reason" -> text(1)
  )).bindFromRequest()(request).fold(
    err => None,
    suc => {
      val status = StringUtil.trim(suc._1)
      val reason = StringUtil.trim(suc._2)
      if (!status.isDefined || !reason.isDefined) {
        None
      } else {
        Some(ActionDataHolder(status.get, reason.get))
      }
    }
  )

  override def validate(): Either[RequestDataHolder,RequestDataHolder] = {
    withValidAsset(assetTag) { asset =>
      if (!params.isDefined) {
        Left(RequestDataHolder.error400("status and reason parameters must be specified"))
      } else if (params.get.isError) {
        Left(RequestDataHolder.error400("invalid status specified"))
      } else {
        Right(params.get)
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

