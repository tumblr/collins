package util

import play.api.i18n.{Lang, Messages}

trait MessageHelperI {
  val parentKey: String

  def message(key: String, args: Any*)(implicit lang: Lang): String = {
    Messages(keyFor(key), args:_*)
  }
  def messageWithDefault(key: String, default: String, args: Any*)(implicit lang: Lang): String = {
    val msg = message(key, args:_*)
    if (msg == keyFor(key)) {
      default
    } else {
      msg
    }
  }
  def rootMessage(key: String, args: Any*)(implicit lang: Lang): String = {
    Messages(key, args:_*)
  }
  def keyFor(k: String) = "%s.%s".format(parentKey, k)
  def fuck(msg: Option[String])(implicit lang: Lang): String =
    msg.getOrElse(rootMessage("error.unknown"))
}

abstract class MessageHelper(override val parentKey: String) extends MessageHelperI
