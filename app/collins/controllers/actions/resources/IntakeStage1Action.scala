package collins.controllers.actions.resources

import scala.concurrent.Future
import scala.concurrent.Await

import scala.concurrent.duration._

import play.api.data.Form
import play.api.data.Forms.of
import play.api.data.Forms.single
import play.api.libs.concurrent.Execution.Implicits.defaultContext
import play.api.mvc.AsyncResult
import play.api.mvc.SimpleResult
import play.api.templates.Html

import collins.controllers.Help
import collins.controllers.SecureController
import collins.controllers.actions.RequestDataHolder
import collins.controllers.actions.SecureAction
import collins.controllers.forms.truthyFormat
import collins.models.Truthy
import collins.power.Identify
import collins.power.management.PowerManagement
import collins.util.IpmiCommand
import collins.util.concurrent.BackgroundProcessor
import collins.util.plugins.IpmiPowerCommand
import collins.util.plugins.PowerManagement
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
      Status.Ok(
        views.html.resources.intake2(definedAsset, IntakeStage2Action.dataForm)(flash, request)
      )
    case dummy => PowerManagement.pluginEnabled match {
      case None =>
        Status.Ok(views.html.help(Help.PowerManagementDisabled)(flash, request))
      case Some(plugin) => 
        Await.result(identifyAsset(plugin), 5 seconds)
    }
  }

  override def handleWebError(rd: RequestDataHolder) = Some(
    Redirect(collins.app.routes.Resources.intake(assetId, 1)).flashing("error" -> rd.toString)
  )

  protected def identifyAsset(plugin: PowerManagement) : Future[SimpleResult] = {
    val cmd = IpmiPowerCommand.fromPowerAction(definedAsset, Identify)
    BackgroundProcessor.flatSend(cmd) { result =>
      IpmiCommand.fromResult(result) match {
        case Left(throwable) =>
          verifyIpmiReachable(plugin, throwable.toString)
        case Right(None) => Future.successful(defaultView)
        case Right(Some(suc)) if suc.isSuccess => Future.successful(defaultView)
        case Right(Some(error)) if !error.isSuccess =>
          verifyIpmiReachable(plugin, error.toString)
      }
    }
  }

  protected def defaultView =
    Status.Ok(views.html.resources.intake(definedAsset)(flash, request))

  protected def verifyIpmiReachable(plugin: PowerManagement, errorString: String): Future[SimpleResult] = {
    val ps = plugin.verify(definedAsset)
    ps.map {
      case reachable if reachable.isSuccess =>
        Status.Ok(views.html.help(Help.IpmiError, errorString)(flash, request))
      case unreachable if !unreachable.isSuccess =>
        Status.Ok(views.html.help(Help.IpmiUnreachable, errorString)(flash, request))
    }
  }
}
