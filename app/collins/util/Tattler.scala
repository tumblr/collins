package collins.util

import play.api.Logger

import collins.models.Asset
import collins.models.AssetLog
import collins.models.User
import collins.models.logs.LogFormat
import collins.models.logs.LogSource

import collins.util.config.Feature

sealed class Tattler(val username: String, val source: LogSource.LogSource) {
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
      asset, username, msg, LogFormat.PlainText, source
    ).create()})
  }
  
  def error(msg: String, asset: Asset): AssetLog = {
    execute(asset, msg, { (asset, msg) => AssetLog.error(
      asset, username, msg, LogFormat.PlainText, source
    ).create()})
  }
  
  def warning(msg: String, asset: Asset): AssetLog = {
    execute(asset, msg, { (asset, msg) => AssetLog.warning(
      asset, username, msg, LogFormat.PlainText, source
    ).create()})
  }
  
  def notice(msg: String, asset: Asset): AssetLog = {
    execute(asset, msg, { (asset, msg) => AssetLog.notice(
      asset, username, msg, LogFormat.PlainText, source
    ).create()})
  }
  
  def note(msg: String, asset: Asset): AssetLog = {
    execute(asset, msg, { (asset, msg) => AssetLog.note(
      asset, username, msg, LogFormat.PlainText, source
    ).create()})
  }
  
  def informational(msg: String, asset: Asset): AssetLog = {
    execute(asset, msg, { (asset, msg) => AssetLog.informational(
      asset, username, msg, LogFormat.PlainText, source
    ).create()})
  }
  
  /** 
   *  NOTE: This is a snowflake, it ignores the log source, 
   *  uses the syslogAsset and is intended for use in system tattling
   **/
  def system(msg: String): AssetLog = {
    try {
      AssetLog.error(
        Feature.syslogAsset, username, msg, LogFormat.PlainText, LogSource.System).create()
    } catch  {
      case e: Throwable =>
        logger.error("Failed to create assetlog", e)
        throw e
    }
  }
}

sealed class ApplicationTattler(user: User, source: LogSource.LogSource) extends Tattler(user.username, source)
object InternalTattler extends Tattler("", LogSource.Internal)

