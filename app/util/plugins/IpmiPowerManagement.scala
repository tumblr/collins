package util
package plugins

import models.{Asset, IpmiInfo}

import akka.util.Duration
import akka.util.duration._
import play.api.{Application, Configuration, PlayException, Plugin}

import com.tumblr.play.{AssetWithTag, PowerManagement, Power, PowerAction, PowerOff}
import com.tumblr.play.{PowerOn, PowerSoft, PowerState, RebootSoft, RebootHard, Verify, Identify}
import com.twitter.util.{Future, FuturePool}
import java.util.concurrent.Executors

object IpmiPowerManagementConfig {
  val PowerOffKey = "powerOff"
  val PowerOnKey = "powerOn"
  val PowerSoftKey = "powerSoft"
  val PowerStateKey = "powerState"
  val RebootHardKey = "rebootHard"
  val RebootSoftKey = "rebootSoft"
  val VerifyKey = "verify"
  val IdentifyKey = "identify"
  val RequiredKeys: Set[String] = Set(
    PowerOffKey, PowerOnKey, PowerSoftKey, PowerStateKey, RebootHardKey, RebootSoftKey, VerifyKey,
    IdentifyKey
  )
  def apply(k: PowerAction): String = k match {
    case PowerOff => PowerOffKey
    case PowerOn => PowerOnKey
    case PowerSoft => PowerSoftKey
    case PowerState => PowerStateKey
    case RebootSoft => RebootSoftKey
    case RebootHard => RebootHardKey
    case Verify => VerifyKey
    case Identify => IdentifyKey
  }
}

case class IpmiPowerCommand(
  override val configKey: String,
  override val ipmiInfo: IpmiInfo,
  override val interval: Duration = 60.seconds,
  val verify: Boolean = false,
  val userTimeout: Option[Duration] = None)
extends IpmiCommand {
  override val timeout = userTimeout.getOrElse(defaultTimeout)
  override def getConfig(): Map[String, String] = {
    Config.toMap("powermanagement")
  }
}

object IpmiPowerCommand {
  private def ipmiErr(a: Asset) =
    throw new IllegalStateException("Could not find IPMI info for asset %s".format(a.tag))

  def apply(asset: Asset, key: String) = IpmiInfo.findByAsset(asset) match {
    case None => ipmiErr(asset)
    case Some(ipmi) => new IpmiPowerCommand(key, ipmi)
  }
  def fromPowerAction(asset: Asset, action: PowerAction) = IpmiInfo.findByAsset(asset) match {
    case None => ipmiErr(asset)
    case Some(ipmi) =>
      val key = IpmiPowerManagementConfig(action)
      new IpmiPowerCommand(key, ipmi)
  }
}

class IpmiPowerManagement(app: Application) extends Plugin with PowerManagement {
  import IpmiPowerManagementConfig._

  protected[this] val executor = Executors.newCachedThreadPool()
  protected[this] val pool = FuturePool(executor)

  protected lazy val configuration: Option[Configuration] = {
    app.configuration.getConfig("powermanagement")
  }

  override def enabled: Boolean = {
    configuration.map { cfg =>
      val isEnabled = cfg.getBoolean("enabled").getOrElse(false)
      val isMe = cfg.getString("class").map(_.contains("IpmiPowerManagement")).getOrElse(false)
      isEnabled && isMe
    }.getOrElse(false)
  }

  override def onStart(): Unit = if (enabled) {
    configuration.map { cfg =>
      RequiredKeys.foreach { key =>
        if (!cfg.getString(key).isDefined) {
          throw InvalidConfig("powermanagement.%s not specified in configuration".format(key))
        }
      }
    }
  }

  override def onStop() {
    try executor.shutdown() catch {
      case _ => // swallow this
    }
  }

  def powerOff(e: AssetWithTag): PowerStatus = run(e, PowerOffKey)
  def powerOn(e: AssetWithTag): PowerStatus = run(e, PowerOnKey)
  def powerSoft(e: AssetWithTag): PowerStatus = run(e, PowerSoftKey)
  def powerState(e: AssetWithTag): PowerStatus = run(e, PowerStateKey)
  def rebootHard(e: AssetWithTag): PowerStatus = run(e, RebootHardKey)
  def rebootSoft(e: AssetWithTag): PowerStatus = run(e, RebootSoftKey)
  def identify(e: AssetWithTag): PowerStatus = run(e, IdentifyKey)
  def verify(e: AssetWithTag): PowerStatus = run(e, VerifyKey)

  protected[this] def run(e: AssetWithTag, configKey: String): PowerStatus = pool {
    IpmiPowerCommand(getAsset(e), configKey).run() match {
      case None => Failure("powermanagement not enabled or available in environment")
      case Some(status) => status.isSuccess match {
        case true => Success(status.output)
        case false => Failure(status.stderr.getOrElse("Error running command for %s".format(configKey)))
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
