package controllers
package actions
package resources

import asset.ActionAttributeHelper
import models.AssetLifecycle
import models.AssetMeta.Enum.{ChassisTag, RackPosition}
import util.{MessageHelperI, SecuritySpecification}
import util.power.PowerUnits
import validators.ParamValidation

import play.api.data.Form
import play.api.data.Forms._
import scala.util.control.Exception.allCatch

trait IntakeStage3Form extends ParamValidation {
  type DataForm = Tuple2[
    String,              // chassis tag
    String               // rack position
  ]
  val dataForm = Form(tuple(
    ChassisTag.toString -> validatedText(1),
    RackPosition.toString -> validatedText(1)
  ))
}
object IntakeStage3Action extends IntakeStage3Form

case class IntakeStage3Action(
  assetId: Long,
  spec: SecuritySpecification,
  handler: SecureController
) extends SecureAction(spec, handler)
    with IntakeAction
    with IntakeStage3Form
    with ActionAttributeHelper
{

  case class ActionDataHolder(
    chassisTag: String,
    rackPosition: String,
    powerMap: Map[String,String]
  ) extends RequestDataHolder

  override def invalidAttributeMessage(p: String) = "Parameter %s invalid".format(p)

  override def validate(): Either[RequestDataHolder,RequestDataHolder] = super.validate() match {
    case Right(_) =>
      dataForm.bindFromRequest()(request).fold(
        error => Left(RequestDataHolder.error400(fieldError(error))),
        success => {
          val (chassisTag, rackPosition) = success
          verifyChassisTag(chassisTag) match {
            case Right(ctag) => validate(ctag, rackPosition)
            case Left(rdh) => Left(rdh)
          }
        }
      )
    case left => left
  }

  override def execute(rd: RequestDataHolder) = rd match {
    case ActionDataHolder(chassisTag, rackPosition, powerMap) =>
      val updateMap = Map(
        ChassisTag.toString -> chassisTag,
        RackPosition.toString -> rackPosition
      ) ++ powerMap
      AssetLifecycle.updateAsset(definedAsset, updateMap) match {
        case Left(error) =>
          handleError(RequestDataHolder.error400(error.getMessage))
        case Right(ok) if ok =>
          Redirect(app.routes.Resources.index).flashing(
            "success" -> rootMessage("asset.intake.success", definedAsset.tag)
          )
        case Right(ok) if !ok =>
          handleError(RequestDataHolder.error400(
            rootMessage("asset.intake.failed", definedAsset.tag)
          ))
      }
  }

  override def handleWebError(rd: RequestDataHolder) = Some(Status.Ok(
    Stage3Template(definedAsset, dataForm)(flash + ("error", rd.toString), request)
  ))

  protected def validate(chassisTag: String, rackPosition: String): Validation = try {
    val pmap = PowerUnits.unitMapFromMap(getInputMap)
    PowerUnits.validateMap(pmap)
    Right(ActionDataHolder(chassisTag, cleanString(rackPosition).get, pmap))
  } catch {
    case error => Left(RequestDataHolder.error400(error.getMessage))
  }

  protected def fieldError(f: Form[_]) = f match {
    case e if e.error(ChassisTag.toString).isDefined => rootMessage("asset.update.chassisTag.invalid")
    case e if e.error(RackPosition.toString).isDefined => rootMessage("asset.update.rackPosition.invalid")
    case other => "Unexpected error occurred"
  }
}
