package collins.util.views

import collins.models.MetaWrapper

case class DecoratorConfigException(source: String, key: String)
  extends Exception("Didn't find key %s in decorator configuration for %s".format(key, source))

case class Decorator(config: DecoratorConfig, parser: DecoratorParser) {
  def format(key: String, value: String): String = {
    parser.parse(value).zipWithIndex.map {
      case (v, i) =>
        newString(key, v, i)
    }.mkString(config.between)
  }
  def format(key: String, values: Seq[String]): String = {
    values.map { value =>
      parser.parse(value).zipWithIndex.map {
        case (v, i) =>
          newString(key, v, i)
      }.mkString(config.between)
    }.mkString(config.between)
  }
  def format(meta: MetaWrapper): String = {
    parser.parse(meta.getValue).zipWithIndex.map {
      case (v, i) =>
        newString(v, i, meta)
    }.mkString(config.between)
  }
  protected def newString(key: String, value: String, idx: Int) = {
    val idxConfig = config.getIndex(idx)
    val replacers: Seq[(String, String)] = Seq(
      ("name", key),
      ("value", value)) ++ idxConfig.toSeq.map {
        case (key, ival) =>
          ("i.%s".format(key), ival)
      }
    replacers.foldLeft(config.decorator) {
      case (total, current) =>
        total.replace("{%s}".format(current._1), current._2)
    }
  }
  protected def newString(value: String, idx: Int, meta: MetaWrapper) = {
    val idxConfig = config.getIndex(idx) // with keys like label, thingamajig, etc
    val replacers: Seq[(String, String)] = Seq(
      ("name", meta.getName),
      ("label", meta.getLabel),
      ("description", meta.getDescription),
      ("value", value)) ++ idxConfig.toSeq.map {
        case (key, ival) =>
          ("i.%s".format(key), ival)
      }
    replacers.foldLeft(config.decorator) {
      case (total, current) =>
        total.replace("{%s}".format(current._1), current._2)
    }
  }
}
