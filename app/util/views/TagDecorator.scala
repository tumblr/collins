package util
package views

import config.Configurable
import models.MetaWrapper

import play.api.Configuration
import play.api.mvc.Content
import play.api.templates.Html

object TagDecoratorConfig extends Configurable {
  override val namespace = "tagdecorators"
  override val referenceConfigFilename = "tagdecorators_reference.conf"

  def decorators = new Configuration(getConfig("decorators"))

  override protected def validateConfig() {
    decorators
  }
}

object TagDecorator {

  // optionalDelimiter only used if no decorator defined
  def decorate(key: String, values: Seq[String], optionalDelimiter: String): Content = {
    getDecorator(key) match {
      case None => Html(values.mkString(optionalDelimiter))
      case Some(d) => Html(d.format(key, values))
    }
  }

  def decorate(key: String, value: String): Content = {
    getDecorator(key) match {
      case None => Html(value)
      case Some(d) => Html(d.format(key, value))
    }
  }

  def decorate(meta: MetaWrapper): Content = {
    getDecorator(meta.getName) match {
      case None => Html(meta.getValue)
      case Some(d) => Html(d.format(meta))
    }
  }

  protected def decorators: Map[String,Decorator] = {
    val decorators = TagDecoratorConfig.decorators
    decorators.subKeys.foldLeft(Map[String,Decorator]()) { case(total,current) =>
      val config = decorators.getConfig(current).get
      Map(current -> createDecorator(current, config)) ++ total
    }
  }

  protected def getDecorator(key: String): Option[Decorator] = {
    decorators.get(key)
  }

  protected def createDecorator(key: String, config: Configuration): Decorator = {
    val decorator = config.getString("decorator").getOrElse {
      throw DecoratorConfigurationException(key, "decorator")
    }
    val parser = config.getString("valueParser")
      .map(c => getClassInstance(c))
      .getOrElse(new IdentityParser)
    Decorator(decorator, parser(key, config), config)
  }

  protected def getClassInstance(s: String) = {
    this.getClass.getClassLoader.loadClass(s).newInstance().asInstanceOf[DecoratorParser]
  }

}
