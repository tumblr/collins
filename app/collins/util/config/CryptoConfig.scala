package collins.util.config

import collins.util.MessageHelper
import collins.util.CryptoCodec

object CryptoConfig extends MessageHelper("crypto") with Configurable {
  override val namespace = parentKey
  override val referenceConfigFilename = "crypto_reference.conf"

  def key = getString("key")(ConfigValue.Required).filter(_.nonEmpty).getOrElse {
    throw globalError("crypto.key must be defined")
  }

  override protected def validateConfig() {
    key
    try {
      CryptoCodec(key).Encode("fizz")
    } catch {
      case e: Throwable =>
        throw globalError(message("missingJCE"))
    }
  }
}
