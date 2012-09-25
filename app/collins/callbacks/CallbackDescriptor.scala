package collins
package callbacks

import util.config.{ActionConfig, ConfigAccessor, ConfigSource, Configurable, ConfigValue, TypesafeConfiguration}

import com.typesafe.config.ConfigValueType
import play.api.Logger


case class MatchConditional(name: String, state: Option[String], states: List[String])


case class CallbackConditional(previous: MatchConditional, current: MatchConditional)


case class CallbackDescriptor(name: String, override val source: TypesafeConfiguration)
  extends ConfigAccessor with ConfigSource
{

  private[this] val logger = Logger("CallbackDescriptor.%s".format(name))

  def on = getString("on")(ConfigValue.Required).get
  def matchCondition = CallbackConditional(
    MatchConditional(name, previousState, previousStates),
    MatchConditional(name, currentState, currentStates)
  )
  def matchAction: Option[ActionConfig] = ActionConfig.getActionConfig(
      getConfig("action"))

  def validateConfig() {
    logger.debug("validateConfig - event - %s".format(getString("on","NONE")))
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

}

