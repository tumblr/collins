package util
package views

import collins.action.ActionConfig
import config.{Configurable, ConfigAccessor, ConfigSource, TypesafeConfiguration}

import com.typesafe.config.{ConfigObject, ConfigValue}


case class DecoratorConfig(val name: String, override val source: TypesafeConfiguration)
  extends ConfigAccessor with ConfigSource
{
  def between = getString("between","")
  def decorator = getString("decorator", "")
  /*def decoratorAction: Option[ActionConfig] = getObjectMap("decoratorAction").map {
    case(name, o) => return Some(ActionConfig(o.toConfig))
    case None => None
  }*/
  def delimiter = getString("delimiter")
  def valueParser = getString("valueParser")
  def getIndex(i: Int): Map[String,String] = getStringMap(i.toString)
  def validateConfig() {
    if (decorator == None ) { //&& decoratorAction == None) {
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

  override protected def validateConfig() {
    decorators.foreach { case(k, v) =>
      v.validateConfig
    }
  }
}
