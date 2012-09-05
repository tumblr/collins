package util
package plugins

import models.{Asset, IpmiInfo}

import akka.util.Duration
import akka.util.duration._
import play.api.{Application, PlayException, Plugin}

import com.tumblr.play.{AssetWithTag, PowerManagement, Power, PowerAction, PowerOff}
import com.tumblr.play.{PowerOn, PowerSoft, PowerState, RebootSoft, RebootHard, Verify, Identify}
import com.twitter.util.{Future, FuturePool}
import java.util.concurrent.{Executors, TimeUnit}

case class IpmiPowerCommand(
  override val ipmiCommand: String,
  override val ipmiInfo: IpmiInfo,
  override val interval: Duration = 60.seconds,
  val verify: Boolean = false,
  val userTimeout: Option[Duration] = None)
extends IpmiCommand {
  override def defaultTimeout = Duration(PowerManagementConfig.timeoutMs, TimeUnit.MILLISECONDS)
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
  protected[this] val executor = Executors.newCachedThreadPool()
  protected[this] val pool = FuturePool(executor)

  override def enabled: Boolean = {
    PowerManagementConfig.pluginInitialize(app.configuration)
    val isEnabled = PowerManagementConfig.enabled
    val isMe = PowerManagementConfig.getClassOption.getOrElse("").contains("IpmiPowerManagement")
    isEnabled && isMe
  }

  override def onStart() {
  }

  override def onStop() {
    try executor.shutdown() catch {
      case _ => // swallow this
    }
  }

  def powerOff(e: AssetWithTag): PowerStatus = run(e, PowerOff)
  def powerOn(e: AssetWithTag): PowerStatus = run(e, PowerOn)
  def powerSoft(e: AssetWithTag): PowerStatus = run(e, PowerSoft)
  def powerState(e: AssetWithTag): PowerStatus = run(e, PowerState)
  def rebootHard(e: AssetWithTag): PowerStatus = run(e, RebootHard)
  def rebootSoft(e: AssetWithTag): PowerStatus = run(e, RebootSoft)
  def identify(e: AssetWithTag): PowerStatus = run(e, Identify)
  def verify(e: AssetWithTag): PowerStatus = run(e, Verify)

  protected[this] def run(e: AssetWithTag, action: PowerAction): PowerStatus = pool {
    IpmiPowerCommand.fromPowerAction(getAsset(e), action).run() match {
      case None => Failure("powermanagement not enabled or available in environment")
      case Some(status) => status.isSuccess match {
        case true => Success(status.stdout)
        case false => Failure(status.stderr.getOrElse("Error running command for %s".format(action)))
      }
    }
  }
  protected[this] def getAsset(e: AssetWithTag): Asset = Asset.findByTag(e.tag) match {
    case Some(a) => a
    case None => throw new IllegalArgumentException(
      "Could not find asset with tag %s".format(e.tag)
    )
  }
  protected[this] def InvalidConfig(s: Option[String] = None): Exception = PlayException(
    "Invalid Configuration",
    s.getOrElse("powermanagement.enabled is true missing configurations"),
    None
  )
  protected[this] def InvalidConfig(s: String): Exception = InvalidConfig(Some(s))
}
