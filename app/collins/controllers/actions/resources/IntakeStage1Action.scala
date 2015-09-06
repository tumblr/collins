package collins.controllers.actions.resources

import scala.concurrent.Future

import play.api.data.Form
import play.api.data.Forms.of
import play.api.data.Forms.single
import play.api.libs.concurrent.Execution.Implicits.defaultContext
import play.api.mvc.Result

import collins.controllers.Help
import collins.controllers.SecureController
import collins.controllers.actions.RequestDataHolder
import collins.controllers.actions.SecureAction
import collins.controllers.forms.truthyFormat
import collins.models.Truthy
import collins.power.Identify
import collins.power.management.IpmiPowerCommand
import collins.power.management.PowerManagement
import collins.power.management.PowerManagementConfig
import collins.util.concurrent.BackgroundProcessor
import collins.util.security.SecuritySpecification

case class IntakeStage1Action(
  assetId: Long,
  spec: SecuritySpecification,
  handler: SecureController
) extends SecureAction(spec, handler) with IntakeAction {

  val dataForm = Form(single(
    "light" -> of[Truthy]
  ))

  case class ActionDataHolder(light: Truthy) extends RequestDataHolder

  override def validate(): Either[RequestDataHolder,RequestDataHolder] = super.validate() match {
    case Left(err) => Left(err)
    case Right(dummy) =>
      dataForm.bindFromRequest()(request).fold(
        err => Right(dummy),
        suc => Right(ActionDataHolder(suc))
      )
  }

  override def execute(rd: RequestDataHolder) = rd match {
    case ActionDataHolder(light) if light.toBoolean =>
      Future {  Status.Ok(
        views.html.resources.intake2(definedAsset, IntakeStage2Action.dataForm)(flash, request)
      ) }
    case dummy => PowerManagementConfig.enabled match {
      case false =>
        Future { Status.Ok(views.html.help(Help.PowerManagementDisabled)(flash, request)) }
      case true =>
        identifyAsset()
    }
  }

  override def handleWebError(rd: RequestDataHolder) = Some(
    Redirect(collins.app.routes.Resources.intake(assetId, 1)).flashing("error" -> rd.toString)
  )

  protected def identifyAsset() : Future[Result] = {
    val cmd = IpmiPowerCommand.fromPowerAction(definedAsset, Identify)
    BackgroundProcessor.flatSend(cmd) { result =>
      result match {
        case Left(throwable) =>
          verifyIpmiReachable(throwable.toString)
        case Right(None) => Future.successful(defaultView)
        case Right(Some(suc)) if suc.isSuccess => Future.successful(defaultView)
        case Right(Some(error)) if !error.isSuccess =>
          verifyIpmiReachable(error.toString)
      }
    }
  }

  protected def defaultView =
    Status.Ok(views.html.resources.intake(definedAsset)(flash, request))

  protected def verifyIpmiReachable(errorString: String): Future[Result] = {
    val ps = PowerManagement.verify(definedAsset)
    ps.map {
      case reachable if reachable.isSuccess =>
        Status.Ok(views.html.help(Help.IpmiError, errorString)(flash, request))
      case unreachable if !unreachable.isSuccess =>
        Status.Ok(views.html.help(Help.IpmiUnreachable, errorString)(flash, request))
    }
  }
}
