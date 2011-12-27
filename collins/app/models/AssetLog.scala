package models

import Model.defaults._
import conversions._
import util.Helpers

import anorm._
import anorm.SqlParser._
import play.api.libs.json._

import java.sql.{Connection, Timestamp}
import java.util.Date

case class AssetLog(
  id: Pk[java.lang.Long],
  asset_id: Id[java.lang.Long],
  created: Timestamp,
  format: java.lang.Byte,
  source: java.lang.Byte,
  message_type: java.lang.Byte,
  message: String)
{
  require(message != null && message.length > 0)

  def toMap(): Map[String,String] = Map(
    "ID" -> getId().toString,
    "ASSET_ID" -> getAssetId().toString,
    "CREATED" -> Helpers.dateFormat(created),
    "FORMAT" -> getFormat().toString,
    "SOURCE" -> getSource().toString,
    "TYPE" -> getMessageType().toString,
    "MESSAGE" -> message
  )
  def toJsonMap(): Map[String,JsValue] = Map(
    "ID" -> JsNumber(getId()),
    "ASSET_ID" -> JsNumber(getAssetId()),
    "CREATED" -> JsString(Helpers.dateFormat(created)),
    "FORMAT" -> JsString(getFormat().toString()),
    "SOURCE" -> JsString(getSource().toString()),
    "TYPE" -> JsString(getMessageType().toString()),
    "MESSAGE" -> (isJson() match {
      case true => Json.parse(message)
      case false => JsString(message)
    })
  )

  def getFormat(): AssetLog.Formats = AssetLog.Formats(format.intValue)
  def isJson(): Boolean = getFormat() == AssetLog.Formats.Json
  def isPlainText(): Boolean = getFormat() == AssetLog.Formats.PlainText

  def getSource(): AssetLog.Sources = AssetLog.Sources(source.intValue)
  def isInternalSource(): Boolean = getSource() == AssetLog.Sources.Internal
  def isApiSource(): Boolean = getSource() == AssetLog.Sources.Api
  def isUserSource(): Boolean = getSource() == AssetLog.Sources.User

  def getMessageType(): AssetLog.MessageTypes = AssetLog.MessageTypes(message_type.intValue)
  def isEmergency(): Boolean =
    getMessageType() == AssetLog.MessageTypes.Emergency
  def isAlert(): Boolean =
    getMessageType() == AssetLog.MessageTypes.Alert
  def isCritical(): Boolean =
    getMessageType() == AssetLog.MessageTypes.Critical
  def isError(): Boolean =
    getMessageType() == AssetLog.MessageTypes.Error
  def isWarning(): Boolean =
    getMessageType() == AssetLog.MessageTypes.Warning
  def isNotice(): Boolean =
    getMessageType() == AssetLog.MessageTypes.Notice
  def isInformational(): Boolean =
    getMessageType() == AssetLog.MessageTypes.Informational
  def isDebug(): Boolean =
    getMessageType() == AssetLog.MessageTypes.Debug

  def getId(): Long = id.get
  def getAssetId(): Long = asset_id.get
  def getAsset(): Asset = Asset.findById(getAssetId()).get

  def withException(ex: Throwable) = {
    val oldMessage = message
    val newMessage = isPlainText() match {
      case true =>
        "Exception Class: %s\nException Message: %s\nMessage: %s".format(
          ex.getClass.toString, ex.getMessage, oldMessage)
      case false => isJson() match {
        case true =>
          val jsMap = Map(
            "Exception Class" -> JsString(ex.getClass.toString),
            "Exception Message" -> JsString(ex.getMessage)
          )
          val json = Json.parse(oldMessage) match {
            case JsObject(map) =>
              JsObject(jsMap ++ map)
            case o => JsObject(jsMap ++ Map(
              "Message" -> o
            ))
          }
          Json.stringify(json)
        case false =>
          throw new Exception("Unhandled format type, not Json or PlainText")
      }
    }
    this.copy(message = newMessage);
  }
}

object AssetLog extends Magic[AssetLog](Some("asset_log")) {
  def apply(asset: Asset, message: String, format: AssetLog.Formats, source: AssetLog.Sources, mt: AssetLog.MessageTypes) = {
    new AssetLog(NotAssigned, Id(asset.getId), new Date().asTimestamp,
      format.id.toByte, source.id.toByte,
      mt.id.toByte,
      message)
  }

  // A "panic" condition - notify all tech staff on call? (earthquake? tornado?) - affects multiple
  // apps/servers/sites...
  def emergency(asset: Asset, message: String, format: AssetLog.Formats, source: AssetLog.Sources) = {
      apply(asset, message, format, source, AssetLog.MessageTypes.Emergency)
  }

  // Should be corrected immediately, but indicates failure in a primary system - fix CRITICAL
  // problems before ALERT - example is loss of primary ISP connection
  def critical(asset: Asset, message: String, format: AssetLog.Formats, source: AssetLog.Sources) = {
      apply(asset, message, format, source, AssetLog.MessageTypes.Critical)
  }

  // Should be corrected immediately - notify staff who can fix the problem - example is loss of
  // backup ISP connection
  def alert(asset: Asset, message: String, format: AssetLog.Formats, source: AssetLog.Sources) = {
      apply(asset, message, format, source, AssetLog.MessageTypes.Alert)
  }

  // Non-urgent failures - these should be relayed to developers or admins; each item must be
  // resolved within a given time
  def error(asset: Asset, message: String, format: AssetLog.Formats, source: AssetLog.Sources) = {
      apply(asset, message, format, source, AssetLog.MessageTypes.Error)
  }

  // Warning messages - not an error, but indication that an error will occur if action is not
  // taken, e.g. file system 85% full - each item must be resolved within a given time
  def warning(asset: Asset, message: String, format: AssetLog.Formats, source: AssetLog.Sources) = {
      apply(asset, message, format, source, AssetLog.MessageTypes.Warning)
  }

  // Events that are unusual but not error conditions - might be summarized in an email to
  // developers or admins to spot potential problems - no immediate action required
  def notice(asset: Asset, message: String, format: AssetLog.Formats, source: AssetLog.Sources) = {
      apply(asset, message, format, source, AssetLog.MessageTypes.Notice)
  }

  // Normal operational messages - may be harvested for reporting, measuring throughput, etc - no
  // action required
  def informational(asset: Asset, message: String, format: AssetLog.Formats, source: AssetLog.Sources) = {
      apply(asset, message, format, source, AssetLog.MessageTypes.Informational)
  }

  //Info useful to developers for debugging the application, not useful during operations
  def debug(asset: Asset, message: String, format: AssetLog.Formats, source: AssetLog.Sources) = {
      apply(asset, message, format, source, AssetLog.MessageTypes.Debug)
  }

  def list(asset: Asset, page: Int = 0, pageSize: Int = 10, sort: String = "DESC", filter: String = ""): Page[AssetLog] = {
    val orderBy: String = sort.toUpperCase match {
      case "ASC" => "ORDER BY ID ASC"
      case _ => "ORDER BY ID DESC"
    }
    val messageFilter = filter.isEmpty match {
      case true => ""
      case false => try {
        "and message_type=%d".format(AssetLog.MessageTypes.withName(filter).id)
      } catch {
        case _ => ""
      }
    }
    val offset = pageSize * page
    // Can't use on() for asc/desc because we don't want it quoted
    Model.withConnection { implicit con =>
      val query = "asset_id={asset_id} %s %s limit {pageSize} offset {offset}".format(messageFilter, orderBy)
      val rows = AssetLog.find(query).on(
        'asset_id -> asset.getId,
        'pageSize -> pageSize,
        'offset -> offset
      ).list()
      val totalRows = AssetLog.count("asset_id={asset_id} %s".format(messageFilter)).on(
        'asset_id -> asset.getId
      ).as(scalar[Long])
      Page(rows, page, offset, totalRows)
    }
  }

  type Formats = Formats.Value
  object Formats extends Enumeration(0) {
    val PlainText = Value(0, "text/plain")
    val Json = Value(1, "application/json")
  }

  type Sources = Sources.Value
  object Sources extends Enumeration(0) {
    val Internal = Value(0, "INTERNAL")
    val Api = Value(1, "API")
    val User = Value(2, "USER")
  }

  type MessageTypes = MessageTypes.Value
  object MessageTypes extends Enumeration(0) {
    val Emergency = Value(0, "EMERGENCY")
    val Alert = Value(1, "ALERT")
    val Critical = Value(2, "CRITICAL")
    val Error = Value(3, "ERROR")
    val Warning = Value(4, "WARNING")
    val Notice = Value(5, "NOTICE")
    val Informational = Value(6, "INFORMATIONAL")
    val Debug = Value(7, "DEBUG")
  }

}
