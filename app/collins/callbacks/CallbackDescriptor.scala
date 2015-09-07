package collins.callbacks

import play.api.Logger

import com.typesafe.config.ConfigValueType

import collins.util.config.ConfigAccessor
import collins.util.config.ConfigSource
import collins.util.config.ConfigValue
import collins.util.config.TypesafeConfiguration

case class MatchConditional(name: String, state: Option[String], states: List[String])
case class CallbackConditional(previous: MatchConditional, current: MatchConditional)

case class CallbackDescriptor(name: String, override val source: TypesafeConfiguration)
    extends ConfigAccessor
    with ConfigSource {
  private[this] val logger = Logger("CallbackDescriptor.%s".format(name))

  def on = getString("on")(ConfigValue.Required).get
  def matchCondition = CallbackConditional(
    MatchConditional(name, previousState, previousStates),
    MatchConditional(name, currentState, currentStates))
  def matchAction = {
    val cfg = getStringMap("action")
    if (cfg.isEmpty) {
      throw CallbackConfigException("action", name)
    }
    val atype = cfg.get("type").flatMap(CallbackActionType(_)).getOrElse(CallbackActionType.Exec)
    atype match {
      case CallbackActionType.Exec =>
        val cmd = getCommand
        ExecActionRunner(cmd)
    }
  }

  def validateConfig() {
    logger.debug("validateConfig - event - %s".format(getString("on", "NONE")))
    on
    logger.debug("validateConfig - matchCondition - %s".format(matchCondition.toString))
    matchCondition
    logger.debug("validateConfig - matchAction - %s".format(matchAction.toString))
    matchAction
  }

  protected def currentState = getString("when.current.state")
  protected def currentStates = getStringList("when.current.states")
  protected def previousState = getString("when.previous.state")
  protected def previousStates = getStringList("when.previous.states")

  // Get an action.command as a sequence of strings, detecting whether the command was specified as
  // a string or as a list
  protected def getCommand(): Seq[String] = {
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
    filtered
  }

}
