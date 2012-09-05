package util

import com.tumblr.play.{Power, PowerAction, PowerManagement => PowerMgmt}

import models.{Asset, AssetType, Status}
import config.{Configurable, ConfigValue}

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
    def assetStateAllowed(a: Asset) = message("disallowStatus", a.getStatus().name)
    def actionAllowed(p: PowerAction) = message("disallowWhenAllocated", p.toString)
    def assetTypeAllowed(a: Asset) = message("allowAssetTypes", a.getType().name)
  }

  def allowAssetTypes: Set[Int] = getStringSet("allowAssetTypes").map { name =>
    AssetType.findByName(name).map(_.id).orElse {
      try {
        Option(AssetType.Enum.withName(name).id)
      } catch {
        case e => None
      }
    }.flatMap(AssetType.findById(_)).getOrElse {
      throw globalError("%s is not a valid asset type".format(name))
    }
  }.map(_.id)
  def disallowStatus: Set[Int] = getStringSet("disallowStatus").map { s =>
    Status.findByName(s).getOrElse {
      throw globalError("%s is not a valid status name".format(s))
    }
  }.map(_.id)
  def disallowWhenAllocated: Set[PowerAction] = getStringSet("disallowWhenAllocated").map { p =>
    Power(p)
  }

  def enabled = getBoolean("enabled", false)
  def getClassOption = getString("class")
  def timeoutMs = getMilliseconds("timeout").getOrElse(10000L)

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
      enabled
      getClassOption
      timeoutMs
      RequiredKeys.foreach { key =>
        val cmd = command(key)
        require(cmd.nonEmpty, "powermanagement.commands.%s must not be empty".format(key))
      }
    }
  }
}


