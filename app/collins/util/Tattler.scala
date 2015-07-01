package collins.util

import play.api.Logger

import collins.models.Asset
import collins.models.AssetLog
import collins.models.User
import collins.models.logs.LogFormat
import collins.models.logs.LogSource

import collins.util.config.Feature

trait Tattler {
  def critical(msg: String, asset: Asset): AssetLog
  def error(msg: String, asset: Asset): AssetLog
  def warning(msg: String, asset: Asset): AssetLog
  def notice(msg: String,  asset: Asset): AssetLog
  def note(msg: String, asset: Asset): AssetLog
  def informational(msg: String, asset: Asset): AssetLog
}

sealed class ApplicationTattler(val user: User, val source: LogSource.LogSource) extends Tattler {
  def message(user: User, msg: String) = {
    "User %s: %s".format(user.username, msg)
  }
    
  def critical(msg: String, asset: Asset = Feature.syslogAsset): AssetLog = {
    AssetLog.critical(
      asset, user.username, message(user, msg), LogFormat.PlainText, source
    ).create()
  }
  
  def error(msg: String, asset: Asset = Feature.syslogAsset): AssetLog = {
    AssetLog.error(
      asset, user.username, message(user, msg), LogFormat.PlainText, source
    ).create()
  }
  
  def warning(msg: String,  asset: Asset = Feature.syslogAsset): AssetLog = {
    AssetLog.warning(
      asset, user.username, message(user, msg), LogFormat.PlainText, source
    ).create()
  }
  
  def notice(msg: String,  asset: Asset = Feature.syslogAsset): AssetLog = {
    AssetLog.notice(
      asset, user.username, message(user, msg), LogFormat.PlainText, source
    ).create()
  }
  
  def note(msg: String, asset: Asset = Feature.syslogAsset): AssetLog = {
    AssetLog.note(
      asset, user.username, message(user, msg), LogFormat.PlainText, source
    ).create()
  }
  
  def informational(msg: String, asset: Asset = Feature.syslogAsset): AssetLog = {
    AssetLog.informational(
      asset, user.username, message(user, msg), LogFormat.PlainText, source
    ).create()
  }
}

object InternalTattler extends Tattler {
  protected[this] val logger = Logger.logger
  
  def execute(asset: Asset, msg: String, f: (Asset, String) => AssetLog): AssetLog = {
    try {
      f(asset, msg)
    } catch  {
      case e: Throwable =>
        logger.error("Failed to create assetlog", e)
        throw e
    }
  }

  def critical(msg: String, asset: Asset): AssetLog = {
    execute(asset, msg, { (asset, msg) => AssetLog.critical(
      asset, "Internal", msg, LogFormat.PlainText, LogSource.Internal
    ).create()})
  }
  
  def error(msg: String, asset: Asset): AssetLog = {
    execute(asset, msg, { (asset, msg) => AssetLog.error(
      asset, "Internal", msg, LogFormat.PlainText, LogSource.Internal
    ).create()})
  }
  
  def warning(msg: String, asset: Asset): AssetLog = {
    execute(asset, msg, { (asset, msg) => AssetLog.warning(
      asset, "Internal", msg, LogFormat.PlainText, LogSource.Internal
    ).create()})
  }
  
  def notice(msg: String, asset: Asset): AssetLog = {
    execute(asset, msg, { (asset, msg) => AssetLog.notice(
      asset, "Internal", msg, LogFormat.PlainText, LogSource.Internal
    ).create()})
  }
  
  def note(msg: String, asset: Asset): AssetLog = {
    execute(asset, msg, { (asset, msg) => AssetLog.note(
      asset, "Internal", msg, LogFormat.PlainText, LogSource.Internal
    ).create()})
  }
  
  def informational(msg: String, asset: Asset): AssetLog = {
    execute(asset, msg, { (asset, msg) => AssetLog.informational(
      asset, "Internal", msg, LogFormat.PlainText, LogSource.Internal
    ).create()})
  }
}

object SystemTattler {
  protected[this] val logger = Logger.logger
  
  def execute(msg: String, f: (String) => AssetLog): AssetLog = {
    try {
      f(msg)
    } catch  {
      case e: Throwable =>
        logger.error("Failed to create assetlog", e)
        throw e
    }
  }

  def critical(msg: String): AssetLog = {
    execute(msg, { (msg) => AssetLog.critical(
      Feature.syslogAsset, "System", msg, LogFormat.PlainText, LogSource.Internal
    ).create()})
  }
  
  def error(msg: String): AssetLog = {
    execute(msg, { (msg) => AssetLog.error(
      Feature.syslogAsset, "System", msg, LogFormat.PlainText, LogSource.Internal
    ).create()})
  }
  
  def warning(msg: String): AssetLog = {
    execute(msg, { (msg) => AssetLog.warning(
      Feature.syslogAsset, "System", msg, LogFormat.PlainText, LogSource.Internal
    ).create()})
  }
  
  def notice(msg: String): AssetLog = {
    execute(msg, { (msg) => AssetLog.notice(
      Feature.syslogAsset, "System", msg, LogFormat.PlainText, LogSource.Internal
    ).create()})
  }
  
  def note(msg: String): AssetLog = {
    execute(msg, { (msg) => AssetLog.note(
      Feature.syslogAsset, "System", msg, LogFormat.PlainText, LogSource.Internal
    ).create()})
  }
  
  def informational(msg: String): AssetLog = {
    execute(msg, { (msg) => AssetLog.informational(
      Feature.syslogAsset, "System", msg, LogFormat.PlainText, LogSource.Internal
    ).create()})
  }
}
