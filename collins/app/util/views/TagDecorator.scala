package util
package views

import models.MetaWrapper

import play.api.Configuration
import play.api.mvc.Content
import play.api.templates.Html

object TagDecorator {

  def decorate(meta: MetaWrapper): Content = {
    getDecorator(meta.getName) match {
      case None => Html(meta.getValue)
      case Some(d) => Html(d.format(meta))
    }
  }

  protected lazy val Decorators: Map[String,Decorator] =
    Helpers.getConfig("tagdecorators").map { decorators =>
      decorators.subKeys.foldLeft(Map[String,Decorator]()) { case(total,current) =>
        val config = decorators.getConfig(current).get
        Map(current -> createDecorator(current, config)) ++ total
      }
    }.getOrElse(Map[String,Decorator]())

  protected def getDecorator(key: String): Option[Decorator] = {
    Decorators.get(key)
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
