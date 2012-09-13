package util
package config

import collins.action.ActionType

import com.typesafe.config.{ConfigValue => TypesafeConfigValue, ConfigValueType}


case class ActionConfigException(component: String)
  extends Exception("Didn't find %s in configuration for action"
      .format(component))


case class ActionConfig(override val source: TypesafeConfiguration)
  extends ConfigAccessor with ConfigSource {

  def actionType = getString("type").flatMap(ActionType(_))
    .getOrElse(ActionType.Exec)
  def command = getCommand

  def validateConfig() {
    command
  }

  /**
   * Gets an action.command as a sequence of strings, detecting whether the
   * command was specified as a String or as a List.
   *
   * @return a Set of Strings, comprising the Action's command
   */
  protected def getCommand(): Seq[String] = {
    val cmd = getConfigValue("command") match {
      case None =>
        throw ActionConfigException("command")
      case Some(v) => v.valueType match {
        case ConfigValueType.LIST =>
          getStringList("command")
        case o =>
          Seq(getString("command")(ConfigValue.Required).get)
      }
    }
    val filtered = cmd.filter(_.nonEmpty)
    if (filtered.isEmpty) {
      throw ActionConfigException("command")
    }
    filtered
  }

}


object ActionConfig {

  def getActionConfig(cfg: TypesafeConfiguration): Option[ActionConfig] = {
    if (!cfg.isEmpty) {
      Some(ActionConfig(cfg))
    } else {
      None
    }
  }

}
