package util
package concurrent

import config.Configurable

object ConcurrencyConfig extends Configurable {
  override val namespace = "concurrency"
  override val referenceConfigFilename = "concurrency_reference.conf"

  def timeoutMs = getMilliseconds("timeout").getOrElse(2000L)

  override protected def validateConfig() {
    timeoutMs
  }
}
