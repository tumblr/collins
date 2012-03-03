package util
package views

import models.MetaWrapper

import play.api.Configuration
import play.api.mvc.Content
import play.api.templates.Html

case class DecoratorConfigurationException(source: String, key: String)
  extends Exception("Didn't find key %s in decorator configuration for %s".format(key))

case class Decorator(decorator: String, parser: DecoratorParser, config: Configuration) {
  private val delimiter: String = config.getString("between").getOrElse("")
  def format(meta: MetaWrapper): String = {
    parser.parse(meta.getValue).zipWithIndex.map { case(v, i) =>
      newString(v, i, meta)
    }.mkString(delimiter)
  }
  protected def newString(value: String, idx: Int, meta: MetaWrapper) = {
    val idxConfig = config.getConfig(idx.toString) // with keys like label, thingamajig, etc
    val replacers: Seq[(String,String)] = Seq(
      ("name", meta.getName),
      ("label", meta.getLabel),
      ("description", meta.getDescription),
      ("value", value)
    ) ++ idxConfig.map { cfg =>
      cfg.keys.foldLeft(Seq[(String,String)]()) { case(total,current) =>
        Seq(("i.%s".format(current), cfg.getString(current).get)) ++ total
      }
    }.getOrElse(Seq[(String,String)]())
    replacers.foldLeft(decorator) { case(total, current) =>
      total.replace("{%s}".format(current._1), current._2)
    }
  }
}
