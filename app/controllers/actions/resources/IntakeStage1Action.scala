package controllers
package actions
package resources

import forms._
import models.Truthy
import util.IpmiCommand
import util.concurrent.BackgroundProcessor
import util.plugins.{IpmiPowerCommand, PowerManagement}
import util.security.SecuritySpecification
import collins.power.Identify
import collins.power.management.{PowerManagement, PowerManagementConfig}
import play.api.data.Form
import play.api.data.Forms._
import play.api.mvc.AsyncResult
import com.twitter.util.Await

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
      case Some(plugin) => AsyncResult {
        identifyAsset(plugin)
      }
    }
  }

  override def handleWebError(rd: RequestDataHolder) = Some(
    Redirect(app.routes.Resources.intake(assetId, 1)).flashing("error" -> rd.toString)
  )

  protected def identifyAsset(plugin: PowerManagement) = {
    val cmd = IpmiPowerCommand.fromPowerAction(definedAsset, Identify)
    BackgroundProcessor.send(cmd) { result =>
      IpmiCommand.fromResult(result) match {
        case Left(throwable) =>
          verifyIpmiReachable(plugin, throwable.toString)
        case Right(None) => defaultView
        case Right(Some(suc)) if suc.isSuccess => defaultView
        case Right(Some(error)) if !error.isSuccess =>
          verifyIpmiReachable(plugin, error.toString)
      }
    }
  }

  protected def defaultView =
    Status.Ok(views.html.resources.intake(definedAsset)(flash, request))

  protected def verifyIpmiReachable(plugin: PowerManagement, errorString: String) = {
    val ps = Await.result(plugin.verify(definedAsset))
    ps match {
      case reachable if reachable.isSuccess =>
        Status.Ok(views.html.help(Help.IpmiError, errorString)(flash, request))
      case unreachable if !unreachable.isSuccess =>
        Status.Ok(views.html.help(Help.IpmiUnreachable, errorString)(flash, request))
    }
  }
}
