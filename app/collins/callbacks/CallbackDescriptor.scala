package collins
package callbacks

import action.{ActionConfig, ActionType}
import util.config.{ConfigAccessor, ConfigSource, Configurable, ConfigValue, TypesafeConfiguration}

import com.typesafe.config.ConfigValueType
import play.api.Logger


case class CallbackConditional(previous: Option[String], current: Option[String])


case class CallbackDescriptor(name: String, override val source: TypesafeConfiguration)
  extends ConfigAccessor with ConfigSource
{
  private[this] val logger = Logger("CallbackDescriptor.%s".format(name))

  def on = getString("on")(ConfigValue.Required).get
  def matchCondition = CallbackConditional(previous.get("state"), current.get("state"))
  def matchAction: ActionConfig = {
    val cfg = getObjectMap("action")
    if (cfg.isEmpty) {
      throw CallbackConfigException("action", name)
    }
    cfg.map { case(name, o) => return ActionConfig(o.toConfig) }
    throw CallbackConfigException("action", name)
  }

  def validateConfig() {
    logger.debug("validateConfig - event - %s".format(getString("on","NONE")))
    on
    logger.debug("validateConfig - matchCondition - %s".format(matchCondition.toString))
    matchCondition
    logger.debug("validateConfig - matchAction - %s".format(matchAction.toString))
    matchAction
  }

  protected def current = getStringMap("when.current")
  protected def previous = getStringMap("when.previous")

}
