package controllers
package actions
package resources

import forms._
import models.Truthy
import play.api.templates.Html
import util.IpmiCommand
import util.concurrent.BackgroundProcessor
import util.plugins.{IpmiPowerCommand, PowerManagement}
import util.security.SecuritySpecification
import collins.power.Identify
import collins.power.management.PowerManagement
import play.api.data.Form
import play.api.data.Forms._
import play.api.mvc.{SimpleResult, AsyncResult}
import scala.concurrent.Future
import play.api.libs.concurrent.Execution.Implicits._


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

  protected def identifyAsset(plugin: PowerManagement) : Future[SimpleResult[Html]] = {
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

  protected def verifyIpmiReachable(plugin: PowerManagement, errorString: String): Future[SimpleResult[Html]] = {
    val ps = plugin.verify(definedAsset)
    ps.map {
      case reachable if reachable.isSuccess =>
        Status.Ok(views.html.help(Help.IpmiError, errorString)(flash, request))
      case unreachable if !unreachable.isSuccess =>
        Status.Ok(views.html.help(Help.IpmiUnreachable, errorString)(flash, request))
    }
  }
}
