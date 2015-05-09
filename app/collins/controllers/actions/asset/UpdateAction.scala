package collins.controllers.actions.asset

import scala.collection.immutable.DefaultMap
import scala.collection.mutable.HashMap

import play.api.data.Form
import play.api.data.Forms.longNumber
import play.api.data.Forms.optional
import play.api.data.Forms.tuple

import collins.controllers.Api
import collins.controllers.SecureController
import collins.controllers.actions.AssetAction
import collins.controllers.actions.RequestDataHolder
import collins.controllers.actions.SecureAction
import collins.controllers.validators.ParamValidation
import collins.models.AssetLifecycle
import collins.models.AssetMeta.Enum.ChassisTag
import collins.models.AssetMeta.Enum.RackPosition
import collins.util.MessageHelper
import collins.util.power.PowerUnits
import collins.util.security.SecuritySpecification

import collins.controllers.actions.asset.UpdateAction.Messages.message

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
          lshw.foreach { l => results("lshw") = l }
          lldp.foreach { l => results("lldp") = l }
          chassisTag.foreach { c => results(ChassisTag.toString) = c }
          rackPosition.foreach { r => results(RackPosition.toString) = r }
          groupId.foreach { g => results("groupId") = g.toString }
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
      val results: collins.models.AssetLifecycle.Status[Boolean] =
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
