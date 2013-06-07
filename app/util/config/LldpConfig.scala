package util
package config

object LldpConfig extends Configurable {
  override val namespace = "lldp"
  override val referenceConfigFilename = "lldp_reference.conf"

  def requireVlanName = getBoolean("requireVlanName", true)

  override protected def validateConfig() {
    requireVlanName
  }
}
