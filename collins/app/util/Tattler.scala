package util

import models._

sealed abstract class Tattler(val source: AssetLog.Sources) {
  protected def message(user: Option[User], msg: String) = {
    "User %s: %s".format(user.map(_.username).getOrElse("unknown"), msg)
  }
  def warning(asset: Asset, user: Option[User], msg: String) = {
    Model.withConnection { implicit con =>
      AssetLog.warning(
        asset, message(user, msg), AssetLog.Formats.PlainText, source
      ).create()
    }
  }
  def note(asset: Asset, user: Option[User], msg: String) = {
    Model.withConnection { implicit con =>
      AssetLog.note(
        asset, message(user, msg), AssetLog.Formats.PlainText, source
      ).create()
    }
  }
  def notice(asset: Asset, user: Option[User], msg: String) = {
    Model.withConnection { implicit con =>
      AssetLog.note(
        asset, message(user, msg), AssetLog.Formats.PlainText, source
      ).create()
    }
  }
}
object UserTattler extends Tattler(AssetLog.Sources.User)
object ApiTattler extends Tattler(AssetLog.Sources.Api)
