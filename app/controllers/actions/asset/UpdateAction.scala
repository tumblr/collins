package controllers
package actions
package asset

import models.AssetLifecycle
import models.AssetMeta.Enum.{ChassisTag, RackPosition}
import util.MessageHelper
import util.power.PowerUnits
import util.security.SecuritySpecification
import validators.ParamValidation

import forms._

import play.api.data.Form
import play.api.data.Forms._
import play.api.data.validation.Constraints._

import collection.immutable.DefaultMap
import collection.mutable.HashMap

object UpdateAction extends ParamValidation {
  object Messages extends MessageHelper("asset.update") {
    def invalidLshw = message("lshw.invalid")
    def invalidLldp = message("lldp.invalid")
    def invalidChassisTag = message("chassisTag.invalid")
    def invalidRackPosition = message("rackPosition.invalid")
    def invalidStatus = rootMessage("asset.status.invalid")
    def invalidState = rootMessage("asset.state.invalid")
    def invalidGroupId = message("groupId.invalid")
  }
  val UpdateForm = Form(tuple(
    "lshw" -> validatedOptionalText(1),
    "lldp" -> validatedOptionalText(1),
    ChassisTag.toString -> validatedOptionalText(1),
    RackPosition.toString -> validatedOptionalText(1),
    "groupId" -> optional(longNumber)
  ))
}

case class UpdateAction(
  assetTag: String,
  spec: SecuritySpecification,
  handler: SecureController
) extends SecureAction(spec, handler)
    with AssetAction
    with ActionAttributeHelper
{

  import UpdateAction.Messages._
  import UpdateAction.UpdateForm

  override def invalidAttributeMessage(s: String) = message("attribute.invalid")

  case class ActionDataHolder(underlying: Map[String,String])
    extends RequestDataHolder
      with DefaultMap[String,String]
  {
    override def get(key: String) = underlying.get(key)
    override def iterator = underlying.iterator
  }

  protected def onlyAttributes: Boolean = {
    val keySet = getInputMap.keySet
    keySet.size match {
      case 1 => keySet("attribute")
      case 2 => keySet("attribute") && keySet("groupId")
      case n => false
    }
  }

  override def validate(): Validation = {
    withValidAsset(assetTag) { asset =>
      val form = UpdateForm.bindFromRequest()(request)
      form.fold(
        error => Left(RequestDataHolder.error400(fieldError(error))),
        success => {
          // drop in attributes first, these have the lowest priority
          val results = new HashMap[String,String]() ++ getAttributeMap
          val (lshw, lldp, chassisTag, rackPosition, groupId) = success
          // all 'known' parameters now, overwrite attributes possibly
          if (lshw.isDefined) results("lshw") = lshw.get
          if (lldp.isDefined) results("lldp") = lldp.get
          if (chassisTag.isDefined) results(ChassisTag.toString) = chassisTag.get
          if (rackPosition.isDefined) results(RackPosition.toString) = rackPosition.get
          if (groupId.isDefined) results("groupId") = groupId.get.toString
          // powerMap has dynamic keys based on configuration
          val powerMap = PowerUnits.unitMapFromMap(getInputMap)
          // FIXME we should merge the power map with existing power values and rerun power validation
          val dh = ActionDataHolder((results ++ powerMap).toMap)
          Right(dh)
        }
      )
    }
  }

  override def execute(rd: RequestDataHolder) = rd match {
    case adh@ActionDataHolder(map) =>
      val results: models.AssetLifecycle.Status[Boolean] =
        if (onlyAttributes)
          AssetLifecycle.updateAssetAttributes(definedAsset, map)
        else
          AssetLifecycle.updateAsset(definedAsset, map)
      results match {
        case Left(exception) =>
          handleError(
            RequestDataHolder.error500("Error updating asset: %s".format(exception.getMessage))
          )
        case Right(false) =>
          handleError(RequestDataHolder.error400("Error updating asset"))
        case Right(true) =>
          Api.statusResponse(true)
      }
  }

  protected def fieldError(f: Form[_]) = f match {
    case e if e.error("lshw").isDefined => invalidLshw
    case e if e.error("lldp").isDefined => invalidLldp
    case e if e.error(ChassisTag.toString).isDefined => invalidChassisTag
    case e if e.error(RackPosition.toString).isDefined => invalidRackPosition
    case e if e.error("groupId").isDefined => invalidGroupId
    case n => "Unexpected error occurred"
  }

}
