package collins.controllers.actions.resources

import play.api.data.Form
import play.api.data.Forms.single

import collins.controllers.SecureController
import collins.controllers.actions.RequestDataHolder
import collins.controllers.actions.SecureAction
import collins.controllers.validators.ParamValidation
import collins.models.AssetMeta.Enum.ChassisTag
import collins.util.security.SecuritySpecification

trait IntakeStage2Form extends ParamValidation {
  val dataForm = Form(single(
    ChassisTag.toString -> validatedText(1)
  ))
}
object IntakeStage2Action extends IntakeStage2Form

case class IntakeStage2Action(
  assetId: Long,
  spec: SecuritySpecification,
  handler: SecureController
) extends SecureAction(spec, handler) with IntakeAction with IntakeStage2Form {

  case class ActionDataHolder(chassisTag: String) extends RequestDataHolder

  override def validate(): Either[RequestDataHolder,RequestDataHolder] = super.validate() match {
    case Left(err) => Left(err)
    case Right(dummy) =>
      dataForm.bindFromRequest()(request).fold(
        error => Left(RequestDataHolder.error400(chassisInvalidMessage)),
        chassisTag => verifyChassisTag(chassisTag) match {
          case Left(err) => Left(err)
          case Right(ctag) => Right(ActionDataHolder(ctag))
        }
      )
  }

  override def execute(rd: RequestDataHolder) = rd match {
    case ActionDataHolder(chassisTag) =>
      val form = IntakeStage3Action.formFromChassisTag(chassisTag)
      Status.Ok(
        Stage3Template(definedAsset, form)(flash, request)
      )
  }

  override def handleWebError(rd: RequestDataHolder) = Some(Status.Ok(
    Stage2Template(definedAsset, dataForm)(flash + ("error", rd.toString), request)
  ))

}
