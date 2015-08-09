package collins.callbacks

import play.api.Logger

import collins.util.config.Configurable

object CallbackConfig extends Configurable {
  private[this] val logger = Logger("CallbackConfig")

  override val namespace = "callbacks"
  override val referenceConfigFilename = "callbacks_reference.conf"

  def enabled = getBoolean("enabled", true)
  def registry: Set[CallbackDescriptor] = getObjectMap("registry").map { case(k,v) =>
    CallbackDescriptor(k, v.toConfig)
  }.toSet

  override def validateConfig() {
    if (enabled) {
      registry.foreach { d =>
        logger.info("Validating callback descriptor %s".format(d.name))
        d.validateConfig
      }
    }
  }
}
