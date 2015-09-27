package collins.firehose

import collins.hazelcast.HazelcastConfig
import collins.callbacks.CallbackConfig
import collins.util.MessageHelper
import collins.util.config.Configurable

object FirehoseConfig extends Configurable {

  override val namespace = "firehose"
  override val referenceConfigFilename = "firehose_reference.conf"

  def enabled = getBoolean("enabled", false)
  def registerTimeoutMs = getMilliseconds("registerTimeout").getOrElse(2000L)

  object Messages extends MessageHelper("firehose") {
    def callbacksNotEnabled =
      messageWithDefault("callbacksNotEnabled", "Firehose requires callbacks, ensure callbacks are enabled.")
    def hazelcastNotEnabled =
      messageWithDefault("hazelcastNotEnabled", "Firehose requires hazelcast, ensure hazelcast is enabled.")
  }

  override protected def validateConfig() {
    if (enabled) {
      require(CallbackConfig.enabled, Messages.callbacksNotEnabled)
      require(HazelcastConfig.enabled, Messages.hazelcastNotEnabled)
    }
  }
}
