package controllers
package actions
package resources

import asset.ActionAttributeHelper
import models.AssetLifecycle
import models.AssetMeta.Enum.{ChassisTag, RackPosition}
import collins.intake.IntakeConfig
import util.MessageHelperI
import util.security.SecuritySpecification
import util.power.{InvalidPowerConfigurationException, PowerUnits}
import validators.ParamValidation

import play.api.data.{Form, FormError}
import play.api.data.Forms._

import java.util.concurrent.atomic.AtomicReference
import scala.util.control.Exception.allCatch

trait IntakeStage3Form extends ParamValidation {

  val intakeCustomFields = IntakeConfig.intake_field_names

  // See http://www.playframework.com/documentation/2.0/ScalaForms
  type DataForm = (
    String, // chassis tag
    String // rack position
   )

  val dataForm = Form(tuple(
    ChassisTag.toString -> validatedText(1),
    RackPosition.toString -> validatedText(1)
  ))

  def formFromChassisTag(tag: String) = dataForm.bind(Map(
    ChassisTag.toString -> tag
  )).copy(errors = Nil)
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

  protected val form = new AtomicReference[Form[DataForm]](dataForm)

  case object ActionError extends RequestDataHolder
  case class IntakeDataHolder(
    chassisTag: String,
    rackPosition: String,
    customFieldMap: Map[String, String],
    powerMap: Map[String, String]
  ) extends RequestDataHolder

  override def invalidAttributeMessage(p: String) = "Parameter %s invalid".format(p)

  override def validate(): Either[RequestDataHolder,RequestDataHolder] = super.validate() match {
    case Right(_) =>
      val boundForm = dataForm.bindFromRequest()(request)
      form.set(boundForm)
      if (boundForm.hasErrors)
        Left(ActionError)
      else {
        val (chassisTag, rackPosition ) = boundForm.get
        verifyChassisTag(chassisTag) match {
          case Right(cleanChassisTag) => {
            validateIntakeFields(cleanChassisTag, rackPosition)
          }
          case Left(rdh) => updateFormErrors(rdh.toString)
        }
      }
    case left => left
  }

  override def execute(rd: RequestDataHolder) = rd match {
    case IntakeDataHolder(chassisTag, rackPosition, customFieldMap, powerMap) => {
      val basicMap = Map(
        ChassisTag.toString -> chassisTag,
        RackPosition.toString -> rackPosition
      )

      val updateMap = basicMap ++ customFieldMap ++ powerMap
      // Asset Lifecycle business
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
  }

  override def handleWebError(rd: RequestDataHolder) = rd match {
    case ActionError =>
      Some(Status.Ok(Stage3Template(definedAsset, form.get)(flash, request)))
    case _ => // if parent validation fails or execution (after validation)
      updateFormErrors(rd.toString)
      Some(Status.Ok(Stage3Template(definedAsset, form.get)(flash, request)))
  }

  private def validateIntakeFields(chassisTag: String, rackPosition: String): Either[RequestDataHolder,RequestDataHolder] = {
    val pmap = PowerUnits.unitMapFromMap(getInputMap)
    PowerUnits.validateMap(pmap)
    val rp = cleanString(rackPosition).get

    Right(IntakeDataHolder(chassisTag, rp, extractFieldValuesFromRequest(), pmap))
  }


  private def extractFieldValuesFromRequest(): Map[String, String] = {
    // Return the extracted values for the custom fields from the request
    intakeCustomFields.map((fieldName: String) => {
      val formValue = request().queryString(fieldName).mkString
          fieldName -> formValue
    }).toMap
  }

  private def updateFormErrors(message: String, key: String = "") = {
    val f = form.get
    val fErrors = f.errors ++ Seq(FormError(key, message, Nil))
    val fData =
      if (key.nonEmpty)
        f.data - key
      else
        f.data
    form.set(f.copy(errors = fErrors, data = fData))
    Left(ActionError)
  }
}
