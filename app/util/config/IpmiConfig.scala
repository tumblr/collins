package util.config

import models.Asset
import models.shared.IpAddressConfig
import util.CryptoCodec

object IpmiConfig extends Configurable {

  override val namespace = "ipmi"
  override val referenceConfigFilename = "ipmi_reference.conf"

  def overwriteConfig(config: TypesafeConfiguration) {
    underlying_=(Some(config))
  }

  def passwordLength = getInt("passwordLength", 12)
  def randomUsername = getBoolean("randomUsername", false)
  def username = getString("username").filter(_.nonEmpty)

  def genUsername(asset: Asset): String = {
    if (randomUsername) {
      CryptoCodec.randomString(8)
    } else if (username.isDefined) {
      username.get
    } else {
      "%s-ipmi".format(asset.tag)
    }
  }

  def get(): Option[IpAddressConfig] = underlying.map { cfg =>
    new IpAddressConfig("IpmiConfig.get", new SimpleAddressConfig(cfg))
  }
  override protected def validateConfig() {
    require(passwordLength > 0 && passwordLength <= 16, "ipmi.passwordLength must be between 1 and 16")
  }
}
