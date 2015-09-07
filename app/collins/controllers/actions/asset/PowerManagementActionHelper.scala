package collins.controllers.actions.asset

import scala.concurrent.Future

import play.api.mvc.Result
import play.api.mvc.Results

import collins.controllers.Api
import collins.controllers.ResponseData
import collins.controllers.SecureController
import collins.controllers.actions.AssetAction
import collins.controllers.actions.RequestDataHolder
import collins.controllers.actions.SecureAction
import collins.models.Asset
import collins.power.Identify
import collins.power.PowerAction
import collins.power.PowerState
import collins.power.Verify
import collins.power.management.IpmiPowerCommand
import collins.power.management.PowerManagement
import collins.power.management.PowerManagementConfig
import collins.shell.CommandResult
import collins.util.concurrent.BackgroundProcessor
import collins.util.config.AppConfig
import collins.util.security.SecuritySpecification

abstract class PowerManagementActionHelper(
  assetTag: String,
  spec: SecuritySpecification,
  handler: SecureController
) extends SecureAction(spec, handler) with AssetAction {

  case class PowerStatusRd(cmd: IpmiPowerCommand) extends RequestDataHolder

  def powerAction(): Option[PowerAction]

  val PowerActionNotFoundMessage = "Power management action must be one of: %s".format(
    PowerManagementConfig.RequiredKeys.mkString(", ")
  )
  val PowerMessages = PowerManagementConfig.Messages

  override def execute(rd: RequestDataHolder): Future[Result] = rd match {
    case PowerStatusRd(cmd) => runCommand(cmd)
  }

  override def validate(): Either[RequestDataHolder,RequestDataHolder] = {
    val asset = assetFromTag(assetTag)
    val pa = powerAction

    if (!PowerManagementConfig.enabled) {
      Left(RequestDataHolder.error501("PowerManagement plugin not enabled"))
    } else if (!asset.isDefined) {
      Left(assetNotFound(assetTag))
    } else if (!pa.isDefined) {
      Left(RequestDataHolder.error400(PowerActionNotFoundMessage))
    } else if (ignoreAsset(asset.get)) {
      Left(RequestDataHolder.error403(FeatureMessages.ignoreDangerousCommands(assetTag)))
    } else if (!assetTypeAllowed(asset.get)) {
      Left(RequestDataHolder.error403(PowerMessages.assetTypeAllowed(asset.get)))
    } else if (!actionAllowed(asset.get, pa.get)) {
      Left(RequestDataHolder.error403(PowerMessages.actionAllowed(pa.get)))
    } else if (!assetStateAllowed(asset.get)) {
      Left(RequestDataHolder.error403(PowerMessages.assetStateAllowed(asset.get)))
    } else {
      setAsset(asset)
      try {
        Right(PowerStatusRd(IpmiPowerCommand.fromPowerAction(asset.get, pa.get)))
      } catch {
        case ex: IllegalStateException =>
          Left(RequestDataHolder.error400(ex.getMessage, ex))
        case ex: Throwable =>
          Left(RequestDataHolder.error500(
            "Unexpected error: %s".format(ex.getMessage), ex
          ))
      }
    }
  }

  // features.ignoreDangerousCommands
  protected def ignoreAsset(asset: Asset): Boolean = AppConfig.ignoreAsset(asset)
  // powermanagement.allowAssetTypes
  protected def assetTypeAllowed(asset: Asset): Boolean = PowerManagement.assetTypeAllowed(asset)
  // !powermanagement.disallowStatus
  protected def assetStateAllowed(asset: Asset): Boolean = PowerManagement.assetStateAllowed(asset)
  // !powermanagement.disallowWhenAllocated
  protected def actionAllowed(asset: Asset, pa: PowerAction): Boolean = {
    PowerManagement.actionAllowed(asset, pa)
  }

  protected def onError(t: Throwable): ResponseData = {
    val msg = "Power event (%s) error: %s%s".format(powerAction.get, t.toString, verifyToString)
    logPowerEvent(msg)
    Api.errorResponse(msg, Results.InternalServerError, Some(t))
  }

  protected def onNoResult(): ResponseData = Api.statusResponse(false)

  protected def onSuccess(s: CommandResult): ResponseData = {
    logSuccessfulPowerEvent()
    Api.statusResponse(true)
  }

  protected def onFailure(s: CommandResult): ResponseData = {
    val msg = "Power event (%s) failed: %s%s".format(powerAction.get, s.toString, verifyToString)
    logPowerEvent(msg)
    Api.errorResponse(msg)
  }

  protected def runCommand(cmd: IpmiPowerCommand) = BackgroundProcessor.send(cmd) { result =>
    result match {
      case Left(throwable) =>
        onError(throwable)
      case Right(None) =>
        onNoResult()
      case Right(Some(suc)) if suc.isSuccess =>
        onSuccess(suc)
      case Right(Some(fail)) if !fail.isSuccess =>
        onFailure(fail)
    }
  }

  protected def verifyToString(): String = {
    verify match {
      case None => ""
      case Some(r) if r => ". Asset %s IS remotely accessible for IPMI access.".format(definedAsset.tag)
      case Some(r) if !r => ". Asset %s IS NOT remotely accessible for IPMI access.".format(definedAsset.tag)
    }
  }

  protected def verify(): Option[Boolean] = {
    if (powerAction.get != Verify) {
      IpmiPowerCommand.fromPowerAction(definedAsset, Verify).run().map { res =>
        res.isSuccess
      }
    } else {
      None
    }
  }

  protected def logPowerEvent(msg: String) {
    val pa = powerAction.get
    pa match {
      case Verify | PowerState | Identify =>
        // don't log verify, state, identify (normal lifecycle)
      case o =>
        tattler.warning(msg, definedAsset)
    }
  }

  protected def logSuccessfulPowerEvent() {
    logPowerEvent("Power event (%s) success".format(powerAction.get.toString))
  }

}
