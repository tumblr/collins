package util

import models._
import java.sql.Connection

sealed abstract class Tattler(val source: AssetLog.Sources, val pString: Option[String] = None) {
  protected def message(user: Option[User], msg: String) = {
    pString.map(s => s + ": " + msg).getOrElse {
      "User %s: %s".format(user.map(_.username).getOrElse("unknown"), msg)
    }
  }
  def warning(asset: Asset, user: Option[User], msg: String): AssetLog = {
    Model.withConnection { con =>
      warning(asset, user, msg, con)
    }
  }
  def warning(asset: Asset, user: Option[User], msg: String, con: Connection): AssetLog = {
    implicit val c: Connection = con
    AssetLog.warning(
      asset, message(user, msg), AssetLog.Formats.PlainText, source
    ).create()
  }
  def notice(asset: Asset, user: Option[User], msg: String) = {
    Model.withConnection { implicit con =>
      AssetLog.notice(
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
  def informational(asset: Asset, user: Option[User], msg: String): AssetLog = {
    Model.withConnection { con =>
      informational(asset, user, msg, con)
    }
  }
  def informational(asset: Asset, user: Option[User], msg: String, con: Connection): AssetLog = {
    implicit val c: Connection = con
    AssetLog.informational(
      asset, message(user, msg), AssetLog.Formats.PlainText, source
    ).create()
  }
}
object UserTattler extends Tattler(AssetLog.Sources.User)
object ApiTattler extends Tattler(AssetLog.Sources.Api)
object InternalTattler extends Tattler(AssetLog.Sources.Internal, Some("Internal"))
