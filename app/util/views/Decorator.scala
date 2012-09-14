package util
package views

import collins.action.Action
import collins.action.handler.AssetActionHandler
import models.asset.AssetView
import models.MetaWrapper

import play.api.Logger
import play.api.mvc.Content


case class DecoratorConfigException(source: String, key: String)
  extends Exception("Didn't find key %s in decorator configuration for %s".format(key, source))


case class Decorator(config: DecoratorConfig, parser: DecoratorParser) {

  protected val logger = Logger("Decorator")

  def format(key: String, value: String): String = {
    if (value.isEmpty) {
      return config.default
    }
    parser.parse(value).zipWithIndex.map { case(v, i) =>
      newString(key, v, i)
    }.mkString(config.between)
  }
  def format(key: String, values: Seq[String]): String = {
    values.map { value =>
      parser.parse(value).zipWithIndex.map { case(v, i) =>
        newString(key, v, i)
      }.mkString(config.between)
    }.mkString(config.between)
  }
  def format(tag: String, asset: AssetView): String = {
    // Executes decorator Collins Action if defined; otherwise, queries for tag
    // value from asset.
    val tagValue: String = config.decoratorAction match {
      case None =>
        ListHelper.getTagValueForAsset(tag, asset).toString
      case Some(actionCfg) => {
        val handler = Action.getHandler[AssetActionHandler] (actionCfg)
        handler match {
          case Some(handler) => handler.executeAssetAction(asset)
          case None => {
            logger.warn("No decorate action handler found for action: %s"
                .format(actionCfg))
            return config.default
          }
        }
      }
    }
    // If no tag value defined, returns configured default value for this tag.
    if (tagValue.isEmpty) {
      return config.default
    }
    // If a formatter action is defined, calls it with the String value of the
    // specified asset metadata tag; otherwise, returns the raw value.
    config.formatterAction match {
      case None => tagValue
      case Some(formatActionCfg) => {
        val handler = Action.getHandler[AssetActionHandler] (formatActionCfg)
        handler match {
          case Some(handler) => handler.formatValue(tagValue)
          case None => {
            logger.warn("No formatter action handler found for action: %s"
                .format(formatActionCfg))
            tagValue
          }
        }
      }
    }
  }
  def format(meta: MetaWrapper): String = {
    parser.parse(meta.getValue).zipWithIndex.map { case(v, i) =>
      newString(v, i, meta)
    }.mkString(config.between)
  }
  protected def newString(key: String, value: String, idx: Int) = {
    val idxConfig = config.getIndex(idx)
    val replacers: Seq[(String,String)] = Seq(
      ("name", key),
      ("value", value)
    ) ++ idxConfig.toSeq.map { case(key, ival) =>
      ("i.%s".format(key), ival)
    }
    replacers.foldLeft(config.decorator) { case(total, current) =>
      total.replace("{%s}".format(current._1), current._2)
    }
  }
  protected def newString(value: String, idx: Int, meta: MetaWrapper) = {
    val idxConfig = config.getIndex(idx) // with keys like label, thingamajig, etc
    val replacers: Seq[(String,String)] = Seq(
      ("name", meta.getName),
      ("label", meta.getLabel),
      ("description", meta.getDescription),
      ("value", value)
    ) ++ idxConfig.toSeq.map { case(key, ival) =>
      ("i.%s".format(key), ival)
    }
    replacers.foldLeft(config.decorator) { case(total, current) =>
      total.replace("{%s}".format(current._1), current._2)
    }
  }
}
