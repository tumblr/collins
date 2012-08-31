package util
package views

import models.MetaWrapper

import play.api.Logger
import play.api.Configuration
import play.api.mvc.Content
import play.api.templates.Html

/**
 * A trait which allows for the values of asset metadata tags to be formatted
 * for display in a manner appropriate to their context.
 */
trait DecoratorBase {

  val DEFAULT_DECORATOR = "{value}"

  def configPrefix: String

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

  lazy val Decorators: Map[String,Decorator] =
    Config.get(configPrefix).map { decorators =>
      decorators.subKeys.map{key => key.replace("\"", "")}
        .foldLeft(Map[String,Decorator]()) { case(total,current) =>
          val config = decorators.getConfig(current).get
          Logger.logger.warn(current)
          Map(current -> createDecorator(current, config)) ++ total
        }
      }.getOrElse(Map[String,Decorator]())

  def getDecorator(key: String): Option[Decorator] = {
    Decorators.get(key)
  }

  protected def createDecorator(key: String, config: Configuration): Decorator = {
    val decorator = config.getString("decorator").getOrElse{ DEFAULT_DECORATOR }
    val parser = config.getString("valueParser")
      .map(c => getClassInstance(c))
      .getOrElse(new IdentityParser)
    Decorator(decorator, parser(key, config), config)
  }

  protected def getClassInstance(s: String) = {
    this.getClass.getClassLoader.loadClass(s).newInstance().asInstanceOf[DecoratorParser]
  }

}


object TagDecorator extends DecoratorBase {

  def configPrefix: String = "tagdecorator"

}
