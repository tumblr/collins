package util
package views

import config.{ActionConfig, Configurable, ConfigAccessor, ConfigSource, TypesafeConfiguration}

import com.typesafe.config.{ConfigObject, ConfigValue}


case class DecoratorConfig(val name: String, override val source: TypesafeConfiguration)
  extends ConfigAccessor with ConfigSource
{

  def between = getString("between", "")
  def decorator = getString("decorator", "")
  def decoratorAction: Option[ActionConfig] =
    ActionConfig.getActionConfig(getConfig("decoratorAction"))
  def default = getString("default", TagDecoratorConfig.default)
  def delimiter = getString("delimiter")
  def formatterAction: Option[ActionConfig] =
    ActionConfig.getActionConfig(getConfig("formatterAction"))
  def header = getString("header", "")
  def showIf: Option[ActionConfig] = 
    ActionConfig.getActionConfig(getConfig("showIf"))
  def valueParser = getString("valueParser")
  def getIndex(i: Int): Map[String,String] = getStringMap(i.toString)

  def validateConfig() {
    if (decorator == None && decoratorAction == None) {
      throw DecoratorConfigException(name, "decorator or decoratorAction")
    }
  }

}


object TagDecoratorConfig extends Configurable {

  override val namespace = "tagdecorators"
  override val referenceConfigFilename = "tagdecorators_reference.conf"

  def decorators: Map[String,DecoratorConfig] = getObjectMap("decorators").map {
    case(name, o) => name -> DecoratorConfig(name, o.toConfig)
  }
  def default = getString("default", "<em>Undefined</em>")

  def getDefaultValue(tag: String): String = {
    if (decorators.contains(tag)) {
      decorators(tag).default
    } else {
      default
    }
  }

  def getHeader(tag: String): String = {
    if (decorators.contains(tag)) {
      if (!decorators(tag).header.isEmpty) {
        decorators(tag).header
      } else {
        ""
      }
    } else {
      ""
    }
  }

  def getShowIf(tag: String): Option[ActionConfig] = {
    if (decorators.contains(tag)) {
      return decorators(tag).showIf
    } else {
      None
    }
  }

  override protected def validateConfig() {
    decorators.foreach { case(k, v) =>
      v.validateConfig
    }
  }

}
