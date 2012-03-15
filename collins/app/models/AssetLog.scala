package models

import conversions._
import util.Helpers

import play.api.libs.json._

import org.squeryl.PrimitiveTypeMode._
import org.squeryl.{Schema, Table}

import java.sql.Timestamp
import java.util.Date

object LogFormat extends Enumeration {
  type LogFormat = LogFormat.Value
  val PlainText = Value(0, "text/plain")
  val Json = Value(1, "application/json")
}
object LogMessageType extends Enumeration {
  type LogMessageType = Value
  val Emergency = Value(0, "EMERGENCY")
  val Alert = Value(1, "ALERT")
  val Critical = Value(2, "CRITICAL")
  val Error = Value(3, "ERROR")
  val Warning = Value(4, "WARNING")
  val Notice = Value(5, "NOTICE")
  val Informational = Value(6, "INFORMATIONAL")
  val Debug = Value(7, "DEBUG")
  val Note = Value(8, "NOTE")
}
object LogSource extends Enumeration {
  type LogSource = Value
  val Internal = Value(0, "INTERNAL")
  val Api = Value(1, "API")
  val User = Value(2, "USER")
}
import LogFormat.LogFormat
import LogMessageType.LogMessageType
import LogSource.LogSource

case class AssetLog(
  asset_id: Long,
  created: Timestamp,
  format: LogFormat = LogFormat.PlainText,
  source: LogSource = LogSource.Internal,
  message_type: LogMessageType = LogMessageType.Emergency,
  message: String,
  id: Long = 0) extends ValidatedEntity[Long]
{
  def this() = // 0 arg constructor since we have enums
    this(0,new Date().asTimestamp, LogFormat.PlainText, LogSource.Internal, LogMessageType.Emergency, "", 0)

  override def validate() {
    require(message != null && message.length > 0)
  }

  def forJsonObject(): Seq[(String,JsValue)] = Seq(
    "ID" -> JsNumber(getId()),
    "ASSET_TAG" -> JsString(Asset.findById(getAssetId()).get.tag),
    "CREATED" -> JsString(Helpers.dateFormat(created)),
    "FORMAT" -> JsString(getFormat().toString()),
    "SOURCE" -> JsString(getSource().toString()),
    "TYPE" -> JsString(getMessageType().toString()),
    "MESSAGE" -> (isJson() match {
      case true => Json.parse(message)
      case false => JsString(message)
    })
  )

  def getFormat(): LogFormat = format
  def isJson(): Boolean = getFormat() == LogFormat.Json
  def isPlainText(): Boolean = getFormat() == LogFormat.PlainText

  def getSource(): LogSource = source
  def isInternalSource(): Boolean = getSource() == LogSource.Internal
  def isApiSource(): Boolean = getSource() == LogSource.Api
  def isUserSource(): Boolean = getSource() == LogSource.User

  def getMessageType(): LogMessageType = message_type
  def isEmergency(): Boolean =
    getMessageType() == LogMessageType.Emergency
  def isAlert(): Boolean =
    getMessageType() == LogMessageType.Alert
  def isCritical(): Boolean =
    getMessageType() == LogMessageType.Critical
  def isError(): Boolean =
    getMessageType() == LogMessageType.Error
  def isWarning(): Boolean =
    getMessageType() == LogMessageType.Warning
  def isNotice(): Boolean =
    getMessageType() == LogMessageType.Notice
  def isInformational(): Boolean =
    getMessageType() == LogMessageType.Informational
  def isDebug(): Boolean =
    getMessageType() == LogMessageType.Debug

  def getId(): Long = id
  def getAssetId(): Long = asset_id
  def getAsset(): Asset = Asset.findById(getAssetId()).get

  def withException(ex: Throwable) = {
    val oldMessage = message
    val newMessage = isPlainText() match {
      case true =>
        "Exception Class: %s\nException Message: %s\nMessage: %s".format(
          ex.getClass.toString, ex.getMessage, oldMessage)
      case false => isJson() match {
        case true =>
          val jsSeq = Seq(
            "Exception Class" -> JsString(ex.getClass.toString),
            "Exception Message" -> JsString(ex.getMessage)
          )
          val json = Json.parse(oldMessage) match {
            case JsObject(seq) =>
              JsObject(jsSeq ++ seq)
            case o => JsObject(jsSeq ++ Seq(
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

  def create() = {
    if (id == 0) {
      AssetLog.create(this)
    } else {
      throw new IllegalStateException("Can't create log entry, already created")
    }
  }

}

object AssetLog extends Schema with AnormAdapter[AssetLog] {

  override val tableDef = table[AssetLog]("asset_log")
  on(tableDef)(a => declare(
    a.id is(autoIncremented,primaryKey),
    columns(a.asset_id, a.message_type) are(indexed)
  ))

  override def delete(t: AssetLog): Int = 0

  def apply(asset: Asset, message: String, format: LogFormat, source: LogSource, mt: LogMessageType) = {
    new AssetLog(asset.getId, new Date().asTimestamp, format, source, mt, message)
  }

  // A "panic" condition - notify all tech staff on call? (earthquake? tornado?) - affects multiple
  // apps/servers/sites...
  def emergency(asset: Asset, message: String, format: LogFormat, source: LogSource) = {
      apply(asset, message, format, source, LogMessageType.Emergency)
  }

  // Should be corrected immediately, but indicates failure in a primary system - fix CRITICAL
  // problems before ALERT - example is loss of primary ISP connection
  def critical(asset: Asset, message: String, format: LogFormat, source: LogSource) = {
      apply(asset, message, format, source, LogMessageType.Critical)
  }

  // Should be corrected immediately - notify staff who can fix the problem - example is loss of
  // backup ISP connection
  def alert(asset: Asset, message: String, format: LogFormat, source: LogSource) = {
      apply(asset, message, format, source, LogMessageType.Alert)
  }

  // Non-urgent failures - these should be relayed to developers or admins; each item must be
  // resolved within a given time
  def error(asset: Asset, message: String, format: LogFormat, source: LogSource) = {
      apply(asset, message, format, source, LogMessageType.Error)
  }

  // Warning messages - not an error, but indication that an error will occur if action is not
  // taken, e.g. file system 85% full - each item must be resolved within a given time
  def warning(asset: Asset, message: String, format: LogFormat, source: LogSource) = {
      apply(asset, message, format, source, LogMessageType.Warning)
  }

  // Events that are unusual but not error conditions - might be summarized in an email to
  // developers or admins to spot potential problems - no immediate action required
  def notice(asset: Asset, message: String, format: LogFormat, source: LogSource) = {
      apply(asset, message, format, source, LogMessageType.Notice)
  }

  def note(asset: Asset, message: String, format: LogFormat, source: LogSource) = {
      apply(asset, message, format, source, LogMessageType.Note)
  }

  // Normal operational messages - may be harvested for reporting, measuring throughput, etc - no
  // action required
  def informational(asset: Asset, message: String, format: LogFormat, source: LogSource) = {
      apply(asset, message, format, source, LogMessageType.Informational)
  }

  //Info useful to developers for debugging the application, not useful during operations
  def debug(asset: Asset, message: String, format: LogFormat, source: LogSource) = {
      apply(asset, message, format, source, LogMessageType.Debug)
  }

  def list(asset: Option[Asset], page: Int = 0, pageSize: Int = 10, sort: String = "DESC", filter: String = ""): Page[AssetLog] = {
    val negate = filter.startsWith("!")
    val messageType = try {
      if (negate) {
        Some(LogMessageType.withName(filter.drop(1).toUpperCase))
      } else {
        Some(LogMessageType.withName(filter.toUpperCase))
      }
    } catch {
      case e => None
    }
    _list(asset.map(_.getId), page, pageSize, sort, messageType, negate)
  }

  private def _list(asset_id: Option[Long], page: Int, pageSize: Int, sort: String, filter: Option[LogMessageType], negate: Boolean = false): Page[AssetLog] = inTransaction {
    val offset = pageSize * page
    val results = from(tableDef)(a =>
      where(whereClause(a, asset_id, filter, negate))
      select(a)
      orderBy(a.id.withSort(sort))
    ).page(offset, pageSize).toList
    val totalCount = from(tableDef)(a =>
      where(whereClause(a, asset_id, filter, negate))
      compute(count)
    )
    Page(results, page, offset, totalCount)
  }

  private def whereClause(a: AssetLog, asset_id: Option[Long], filter: Option[LogMessageType], negate: Boolean) = {
    filter match {
      case None =>
        (a.asset_id === asset_id.?)
      case Some(f) =>
        (a.asset_id === asset_id.?) and {
          if (negate) {
            (a.message_type <> filter.get)
          } else {
            (a.message_type === filter.get)
          }
        }
    }
  }

}
