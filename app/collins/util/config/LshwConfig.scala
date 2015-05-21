package collins.util.config

object LshwConfig extends Configurable {
  override val namespace = "lshw"
  override val referenceConfigFilename = "lshw_reference.conf"

  def flashProduct = getString("flashProduct", "flashmax").toLowerCase
  def flashProducts = Set(flashProduct) ++ getStringSet("flashProducts", Set[String]()).map(s => s.toLowerCase)
  def flashSize = getLong("flashSize", 1400000000000L)
  def includeDisabledCpu = getBoolean("includeDisabledCpu", false)
  def includeEmptySocket = getBoolean("includeEmptySocket", false)
  def defaultNicCapacity = getString("defaultNicCapacity")(ConfigValue.Optional)

  override protected def validateConfig() {
    flashProduct
    flashProducts
    flashSize
    includeDisabledCpu
    includeEmptySocket
    defaultNicCapacity
  }
}
