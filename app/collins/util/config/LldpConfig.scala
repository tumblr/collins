package collins.util.config

object LldpConfig extends Configurable {
  override val namespace = "lldp"
  override val referenceConfigFilename = "lldp_reference.conf"

  def requireVlanName = getBoolean("requireVlanName", true)
  def requireVlanId = getBoolean("requireVlanId", false)

  override protected def validateConfig() {
    requireVlanName
    requireVlanId
  }
}
