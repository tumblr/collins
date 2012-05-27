package controllers
package actions
package asset

import models.{AssetLifecycle, Status => AStatus}
import models.AssetMeta.Enum.{ChassisTag, RackPosition}
import util.{MessageHelperI, SecuritySpecification}
import validators.StringUtil

import forms._

import play.api.data.Form
import play.api.data.Forms._
import play.api.data.validation.Constraints._

trait PowerUnitRequestHelper {
  def powerUnits: Map[String, String]
}

case class UpdateAction(
  assetTag: String,
  spec: SecuritySpecification,
  handler: SecureController
) extends SecureAction(spec, handler) with AssetAction with ActionAttributeHelper with MessageHelperI {

  override val parentKey: String = "assetupdate"

  case class ActionDataHolder(
    lshw: Option[String],
    lldp: Option[String],
    chassisTag: Option[String],
    attributes: AttributeMap,
    rackPosition: Option[String],
    assetStatus: Option[AStatus.Enum],
    groupId: Option[Long],
    override val powerUnits: Map[String,String]
  ) extends RequestDataHolder with PowerUnitRequestHelper {
  }

  def optionalText(len: Int) = optional(
    text(len).verifying { txt =>
      StringUtil.trim(txt) match {
        case None => false
        case Some(v) => v.length >= len
      }
    }
  )

  override def invalidAttributeMessage(s: String) = message("attribute.invalid")

  lazy val dataForm = Form(tuple(
    "lshw" -> optionalText(1),
    "lldp" -> optionalText(1),
    ChassisTag.toString -> optionalText(1),
    RackPosition.toString -> optionalText(1),
    "status" -> optional(of[AStatus.Enum]),
    "groupId" -> optional(longNumber)
  ))

  override def validate(): Either[RequestDataHolder,RequestDataHolder] = {
    withValidAsset(assetTag) { asset =>
      val form = dataForm.bindFromRequest()(request)
      form.fold(
        error => Left(RequestDataHolder.error400(fieldError(error))),
        success => {
          val (lshw, lldp, chassisTag, rackPosition, status, groupId) = success
          val attributes = getAttributeMap
          val dh = ActionDataHolder(
            lshw, lldp, chassisTag, attributes, rackPosition, status, groupId, Map.empty
          )
          Right(dh)
        }
      )
    }
  }

  override def execute(rd: RequestDataHolder) = null

  protected def fieldError(f: Form[_]) = f match {
    case e if e.error("lshw").isDefined => message("lshw.invalid")
    case e if e.error("lldp").isDefined => message("lldp.invalid")
    case e if e.error(ChassisTag.toString).isDefined => message("chassisTag.invalid")
    case e if e.error(RackPosition.toString).isDefined => message("rackPosition.invalid")
    case e if e.error("status").isDefined => rootMessage("asset.status.invalid")
    case e if e.error("groupId").isDefined => message("groupId.invalid")
    case n => "Unexpected error occurred"
  }

}
