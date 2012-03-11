package util

import models._
import java.sql.Connection
import models.{LogMessageType, LogFormat, LogSource}

sealed abstract class Tattler(val source: LogSource.LogSource, val pString: Option[String] = None) {
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
      asset, message(user, msg), LogFormat.PlainText, source
    ).create()
  }
  def notice(asset: Asset, user: Option[User], msg: String) = {
    Model.withConnection { implicit con =>
      AssetLog.notice(
        asset, message(user, msg), LogFormat.PlainText, source
      ).create()
    }
  }
  def note(asset: Asset, user: Option[User], msg: String) = {
    Model.withConnection { implicit con =>
      AssetLog.note(
        asset, message(user, msg), LogFormat.PlainText, source
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
      asset, message(user, msg), LogFormat.PlainText, source
    ).create()
  }
}
object UserTattler extends Tattler(LogSource.User)
object ApiTattler extends Tattler(LogSource.Api)
object InternalTattler extends Tattler(LogSource.Internal, Some("Internal"))
