package util
package config

object CryptoConfig extends Configurable {
  override val namespace = "crypto"
  override val referenceConfigFilename = "crypto_reference.conf"

  def key = getString("key")(ConfigValue.Required).filter(_.nonEmpty).getOrElse {
    throw globalError("crypto.key must be defined")
  }

  override protected def validateConfig() {
    key
  }
}
