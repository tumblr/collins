package util
package config

object LshwConfig extends Configurable {
  override val namespace = "lshw"
  override val referenceConfigFilename = "lshw_reference.conf"

  def flashProduct = getString("flashProduct", "flashmax").toLowerCase
  def flashSize = getLong("flashSize", 1400000000000L)

  override protected def validateConfig() {
    flashProduct
    flashSize
  }
}
