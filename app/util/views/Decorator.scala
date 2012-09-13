package util
package views

import collins.action.Action
import models.asset.AssetView
import models.MetaWrapper

import play.api.mvc.Content


case class DecoratorConfigException(source: String, key: String)
  extends Exception("Didn't find key %s in decorator configuration for %s".format(key, source))


case class Decorator(config: DecoratorConfig, parser: DecoratorParser) {
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
    val tagValue = config.decoratorAction match {
      case None => format(tag,
          ListHelper.getTagValueForAsset(tag, asset).toString)
      case Some(actionCfg) => {
        val executor = Action.getExecutor(actionCfg)
        executor.executeAssetAction(asset)
      }
    }
    if (tagValue.isEmpty) {
      return config.default
    }
    config.formatterAction match {
      case None => tagValue
      case Some(formatActionCfg) => {
        val executor = Action.getExecutor(formatActionCfg)
        executor.formatValue(tagValue)
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
