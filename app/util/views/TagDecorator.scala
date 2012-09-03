package util
package views

import models.MetaWrapper

import play.api.mvc.Content
import play.api.templates.Html

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
    decorators.map { case(name, config) =>
      name -> createDecorator(config)
    }
  }

  protected def getDecorator(key: String): Option[Decorator] = {
    decorators.get(key)
  }

  protected def createDecorator(config: DecoratorConfig): Decorator = {
    val decorator = config.decorator
    val parser = config.valueParser
      .map(c => getClassInstance(c))
      .getOrElse(new IdentityParser)
    Decorator(config, parser(config.name, config))
  }

  protected def getClassInstance(s: String) = {
    this.getClass.getClassLoader.loadClass(s).newInstance().asInstanceOf[DecoratorParser]
  }

}
