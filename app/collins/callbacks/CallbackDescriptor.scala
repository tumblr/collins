package collins
package callbacks

import util.config.{ConfigAccessor, ConfigSource, ConfigValue, TypesafeConfiguration}
import com.typesafe.config.ConfigValueType

import play.api.Logger

case class CallbackConditional(previous: Option[String], current: Option[String])
case class CallbackAction(command: Seq[String], actionType: CallbackActionType = CallbackActionType.Exec)

case class CallbackDescriptor(name: String, override val source: TypesafeConfiguration)
  extends ConfigAccessor
    with ConfigSource
{
  private[this] val logger = Logger("CallbackDescriptor.%s".format(name))

  def on = getString("on")(ConfigValue.Required).get
  def matchCondition = CallbackConditional(previous.get("state"), current.get("state"))
  def matchAction = {
    val cfg = getStringMap("action")
    if (cfg.isEmpty) {
      throw CallbackConfigException("action", name)
    }
    val atype = cfg.get("type").flatMap(CallbackActionType(_)).getOrElse(CallbackActionType.Exec)
    val cmd = getConfigValue("action.command") match {
      case None =>
        throw CallbackConfigException("command", "%s.action".format(name))
      case Some(v) => v.valueType match {
        case ConfigValueType.LIST =>
          getStringList("action.command")
        case o =>
          Seq(getString("action.command")(ConfigValue.Required).get)
      }
    }
    val filtered = cmd.filter(_.nonEmpty)
    if (filtered.isEmpty) {
      throw CallbackConfigException("command", "%s.action".format(name))
    }
    CallbackAction(filtered, atype)
  }

  protected def current = getStringMap("when.current")
  protected def previous = getStringMap("when.previous")

  def validateConfig() {
    logger.debug("validateConfig - event - %s".format(getString("on","NONE")))
    on
    logger.debug("validateConfig - matchCondition - %s".format(matchCondition.toString))
    matchCondition
    logger.debug("validateConfig - matchAction - %s".format(matchAction.toString))
    matchAction
  }
}
