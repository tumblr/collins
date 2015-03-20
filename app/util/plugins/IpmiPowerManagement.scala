package util
package plugins

import models.{Asset, IpmiInfo}
import play.api.{Application, Plugin}
import collins.power._
import collins.power.management._
import java.util.concurrent.Executors
import scala.concurrent.duration._
import util.concurrent.BackgroundProcessor
import play.api.libs.concurrent.Execution.Implicits._

case class IpmiPowerCommand(
  override val ipmiCommand: String,
  override val ipmiInfo: IpmiInfo,
  override val interval: Duration = 60.seconds,
  verify: Boolean = false,
  userTimeout: Option[FiniteDuration] = None)
extends IpmiCommand {
  override def defaultTimeout = PowerManagementConfig.timeoutMs.milliseconds
  override val timeout = userTimeout.getOrElse(defaultTimeout)
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

  private def ipmiErr(a: Asset) =
    throw new IllegalStateException("Could not find IPMI info for asset %s".format(a.tag))

  def fromPowerAction(asset: Asset, action: PowerAction) = IpmiInfo.findByAsset(asset) match {
    case None => ipmiErr(asset)
    case Some(ipmi) =>
      val cmd = commandFor(action)
      new IpmiPowerCommand(cmd, ipmi)
  }
}

class IpmiPowerManagement(app: Application) extends Plugin with PowerManagement {

  override def enabled: Boolean = {
    PowerManagementConfig.pluginInitialize(app.configuration)
    val isEnabled = PowerManagementConfig.enabled
    val isMe = PowerManagementConfig.getClassOption.getOrElse("").contains("IpmiPowerManagement")
    isEnabled && isMe
  }

  override def onStart() {
  }

  override def onStop() {
  }

  def powerOff(e: Asset): PowerStatus = run(e, PowerOff)
  def powerOn(e: Asset): PowerStatus = run(e, PowerOn)
  def powerSoft(e: Asset): PowerStatus = run(e, PowerSoft)
  def powerState(e: Asset): PowerStatus = run(e, PowerState)
  def rebootHard(e: Asset): PowerStatus = run(e, RebootHard)
  def rebootSoft(e: Asset): PowerStatus = run(e, RebootSoft)
  def identify(e: Asset): PowerStatus = run(e, Identify)
  def verify(e: Asset): PowerStatus = run(e, Verify)

  protected[this] def run(e: Asset, action: PowerAction): PowerStatus = {
    BackgroundProcessor.send(IpmiPowerCommand.fromPowerAction(getAsset(e), action))(result => result match {
      case (Some(error), None) => 
	    Failure("Error running command for %s".format(error.getMessage()))
      case (None, Some(statusOpt)) => statusOpt match { 
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
