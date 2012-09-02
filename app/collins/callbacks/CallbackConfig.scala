package collins
package callbacks

import util.config.Configurable
import play.api.Logger

object CallbackConfig extends Configurable {
  private[this] val logger = Logger("CallbackConfig")

  override val namespace = "callbacks"
  override val referenceConfigFilename = "callbacks_reference.conf"

  def enabled = getBoolean("enabled", true)
  def className = getString("class", "collins.callbacks.CallbackManagerPlugin")
  def registry: Set[CallbackDescriptor] = getObjectMap("registry").map { case(k,v) =>
    CallbackDescriptor(k, v.toConfig)
  }.toSet

  override def validateConfig() {
    if (enabled) {
      className
      registry.foreach { d =>
        logger.info("Validating callback descriptor %s".format(d.name))
        d.validateConfig
      }
    }
  }
}
