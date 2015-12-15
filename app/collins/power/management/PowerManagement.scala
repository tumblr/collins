package collins.power.management

import java.util.concurrent.TimeUnit

import scala.concurrent.Future
import scala.concurrent.duration.Duration
import scala.concurrent.duration.DurationInt
import scala.concurrent.duration.FiniteDuration

import play.api.Logger

import collins.models.Asset
import collins.models.IpmiInfo
import collins.power.Identify
import collins.power.PowerAction
import collins.power.PowerOff
import collins.power.PowerOn
import collins.power.PowerSoft
import collins.power.PowerState
import collins.power.RebootHard
import collins.power.RebootSoft
import collins.power.Verify
import collins.util.PowerCommand
import collins.util.concurrent.BackgroundProcessor

case class IpmiPowerCommand(
  override val ipmiCommand: String,
  override val ipmiInfo: Option[IpmiInfo],
  override val assetTag: String,
  override val interval: Duration = 60.seconds,
  verify: Boolean = false,
  userTimeout: Option[FiniteDuration] = None)
extends PowerCommand {
  override val timeout = userTimeout.getOrElse(Duration(PowerManagementConfig.timeoutMs, TimeUnit.MILLISECONDS))
}

object IpmiPowerCommand {
  val PMC = PowerManagementConfig
  def commandFor(k: PowerAction): String = k match {
    case PowerOff => PMC.powerOffCommand
    case PowerOn => PMC.powerOnCommand
    case PowerSoft => PMC.powerSoftCommand
    case PowerState => PMC.powerStateCommand
    case RebootSoft => PMC.rebootSoftCommand
    case RebootHard => PMC.rebootHardCommand
    case Verify => PMC.verifyCommand
    case Identify => PMC.identifyCommand
  }

  def fromPowerAction(asset: Asset, action: PowerAction) = IpmiInfo.findByAsset(asset) match {
    case None =>
      if (PMC.allowAssetsWithoutIpmi) {
        val cmd = commandFor(action)
        new IpmiPowerCommand(cmd, None, asset.tag)
      } else {
        throw new IllegalStateException("No IPMI configuration for asset %s".format(asset.tag))
      }
    case Some(ipmi) =>
      val cmd = commandFor(action)
      new IpmiPowerCommand(cmd, Some(ipmi), asset.tag)
  }
}

sealed trait PowerCommandStatus {
  def isSuccess: Boolean
  def description: String
}

case class Success(override val description: String = "Command successful") extends PowerCommandStatus {
  override val isSuccess = true
}

case object RateLimit extends PowerCommandStatus {
  override val isSuccess = false
  override val description = "Only one power event every 20 minutes is allowed"
}

case class Failure(override val description: String = "Failed to execute power command") extends PowerCommandStatus {
  override val isSuccess = false
}

sealed trait PowerManagement  {
  protected[this] val logger = Logger(getClass)

  def powerOff(e: Asset): Future[PowerCommandStatus] = run(e, PowerOff)
  def powerOn(e: Asset): Future[PowerCommandStatus] = run(e, PowerOn)
  def powerSoft(e: Asset): Future[PowerCommandStatus] = run(e, PowerSoft)
  def powerState(e: Asset): Future[PowerCommandStatus] = run(e, PowerState)
  def rebootHard(e: Asset): Future[PowerCommandStatus] = run(e, RebootHard)
  def rebootSoft(e: Asset): Future[PowerCommandStatus] = run(e, RebootSoft)
  def identify(e: Asset): Future[PowerCommandStatus] = run(e, Identify)
  def verify(e: Asset): Future[PowerCommandStatus] = run(e, Verify)

  def assetTypeAllowed(asset: Asset): Boolean = {
    val isTrue = PowerManagementConfig.allowAssetTypes.contains(asset.assetTypeId)
    logger.debug("assetTypeAllowed: %s".format(isTrue.toString))
    isTrue
  }

  def assetStateAllowed(asset: Asset): Boolean = {
    val isFalse = !PowerManagementConfig.disallowStatus.contains(asset.statusId)
    logger.debug("assetStateAllowed: %s".format(isFalse.toString))
    isFalse
  }

  def actionAllowed(asset: Asset, action: PowerAction): Boolean = {
    if (asset.isAllocated && PowerManagementConfig.disallowWhenAllocated.contains(action)) {
      false
    } else {
      true
    }
  }

  def powerAllowed(asset: Asset): Boolean = {
    assetStateAllowed(asset) && PowerManagementConfig.enabled && assetTypeAllowed(asset)
  }

  def run(e: Asset, action: PowerAction): Future[PowerCommandStatus]
}

object PowerManagement extends PowerManagement {

  override def run(e: Asset, action: PowerAction): Future[PowerCommandStatus] = {
    BackgroundProcessor.send(IpmiPowerCommand.fromPowerAction(getAsset(e), action))(result => result match {
      case Left(error) =>
      Failure("Error running command for %s".format(error.getMessage()))
      case Right(statusOpt) => statusOpt match {
        case Some(status) => status.isSuccess match {
          case true => Success(status.stdout)
          case false => Failure(status.stderr.getOrElse("Error running command for %s".format(action)))
        }
        case _ => Success("Command for %s succeeded".format(action))
      }
      case _ => Failure("Error running command for %s".format(action))
    })
  }

  protected[this] def getAsset(e: Asset): Asset = Asset.findByTag(e.tag) match {
    case Some(a) => a
    case None => throw new IllegalArgumentException(
      "Could not find asset with tag %s".format(e.tag)
    )
  }
}
