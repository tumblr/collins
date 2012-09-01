package util
package views

import config.{Configurable, ConfigAccessor, ConfigSource, TypesafeConfiguration}

case class DecoratorConfig(val name: String, override val source: TypesafeConfiguration)
  extends ConfigAccessor with ConfigSource
{
  def between = getString("between","")
  def decorator = getString("decorator").getOrElse {
    throw DecoratorConfigurationException(name, "decorator")
  }
  def delimiter = getString("delimiter")
  def valueParser = getString("valueParser")
  def getIndex(i: Int): Map[String,String] = getObjectMap(i.toString).map { case(name, o) =>
    name -> o.get(name).render()
  }
  def validateConfig() {
    decorator
  }
}

object TagDecoratorConfig extends Configurable {
  override val namespace = "tagdecorators"
  override val referenceConfigFilename = "tagdecorators_reference.conf"

  def decorators: Map[String,DecoratorConfig] = getObjectMap("decorators").map { case(name, o) =>
    name -> DecoratorConfig(name, o.toConfig)
  }

  override protected def validateConfig() {
    decorators.foreach { case(k, v) =>
      v.validateConfig
    }
  }
}
