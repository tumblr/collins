package util

import play.api.i18n.{Lang, Messages}

abstract class MessageHelper(parentKey: String) {
  def message(key: String, args: Any*)(implicit lang: Lang): String = {
    Messages(keyFor(key), args:_*)
  }
  def rootMessage(key: String, args: Any*)(implicit lang: Lang): String = {
    Messages(key, args:_*)
  }
  def keyFor(k: String) = "%s.%s".format(parentKey, k)
}
