package util
package config

object LshwConfig extends Configurable {
  override val namespace = "lshw"
  override val referenceConfigFilename = "reference_lshw.conf"

  def flashProduct = getString("flashProduct", "none").toLowerCase
  def flashSize = getLong("flashSize", 0L)

  override protected def validateConfig() {
    flashProduct
    flashSize
  }
}
