package collins.power.management

import collins.models.Asset
import collins.models.AssetType
import collins.models.Status
import collins.power.PowerAction
import collins.util.MessageHelper
import collins.util.config.ConfigValue
import collins.util.config.Configurable

object PowerManagementConfig extends Configurable {

  override val namespace = "powermanagement"
  override val referenceConfigFilename = "powermanagement_reference.conf"

  // commands.powerOff, commands.powerOn, etc
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

  object Messages extends MessageHelper(namespace) {
    def assetStateAllowed(a: Asset) = message("disallowStatus", a.getStatusName)
    def actionAllowed(p: PowerAction) = message("disallowWhenAllocated", p.toString)
    def assetTypeAllowed(a: Asset) = message("allowAssetTypes", a.assetType.name)
  }

  def allowAssetTypes: Set[Int] = getStringSet("allowAssetTypes").map { name =>
    AssetType.findByName(name) match {
      case None =>
        throw globalError("%s is not a valid asset type".format(name))
      case Some(a) => a.id
    }
  }.toSet
  def disallowStatus: Set[Int] = getStringSet("disallowStatus").map { s =>
    Status.findByName(s).getOrElse {
      throw globalError("%s is not a valid status name".format(s))
    }
  }.map(_.id)
  def disallowWhenAllocated: Set[PowerAction] = getStringSet("disallowWhenAllocated").map { p =>
    PowerAction(p)
  }

  def enabled = getBoolean("enabled", false)
  def timeoutMs = getMilliseconds("timeout").getOrElse(10000L)
  // allowAssetsWithoutIpmi (default: false) - If false, only allow power management of assets
  // with valid IPMI details. If true, restricts templating of the IPMI commands to
  // only <tag>.
  def allowAssetsWithoutIpmi = getBoolean("allowAssetsWithoutIpmi").getOrElse(false)

  def powerOffCommand = command(PowerOffKey)
  def powerOnCommand = command(PowerOnKey)
  def powerSoftCommand = command(PowerSoftKey)
  def powerStateCommand = command(PowerStateKey)
  def rebootHardCommand = command(RebootHardKey)
  def rebootSoftCommand = command(RebootSoftKey)
  def verifyCommand = command(VerifyKey)
  def identifyCommand = command(IdentifyKey)

  protected def command(cmd: String): String = getString("commands.%s".format(cmd))(ConfigValue.Required).get

  override protected def validateConfig() {
    if (enabled) {
      allowAssetTypes
      disallowStatus
      disallowWhenAllocated
      timeoutMs
      allowAssetsWithoutIpmi
      RequiredKeys.foreach { key =>
        val cmd = command(key)
        require(cmd.nonEmpty, "powermanagement.commands.%s must not be empty".format(key))
      }
    }
  }
}


