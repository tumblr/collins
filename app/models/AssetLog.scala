package models

import conversions._
import logs._
import LogFormat.LogFormat
import LogMessageType.LogMessageType
import LogSource.LogSource
import util.views.Formatter.dateFormat

import play.api.libs.json._
import Json.toJson

import org.squeryl.PrimitiveTypeMode._
import org.squeryl.{Schema, Table}
import org.squeryl.dsl.ast.{BinaryOperatorNodeLogicalBoolean, LogicalBoolean}

import java.sql.Timestamp
import java.util.Date

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
  override def asJson: String = toJsValue.toString
  def toJsValue() = toJson(this)

  def getFormat(): LogFormat = format
  def isJson(): Boolean = getFormat() == LogFormat.Json
  def isPlainText(): Boolean = getFormat() == LogFormat.PlainText

  def getSource(): LogSource = source
  def isInternalSource(): Boolean = getSource() == LogSource.Internal
  def isApiSource(): Boolean = getSource() == LogSource.Api
  def isUserSource(): Boolean = getSource() == LogSource.User
  def isSystemSource(): Boolean = getSource() == LogSource.System

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
  def getAssetTag(): String = getAsset().tag
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

  override val createEventName = Some("asset_log_create")

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

  override def get(log: AssetLog) = getOrElseUpdate("AssetLog.get(%d)".format(log.id)) {
    tableDef.lookup(log.id).get
  }

  def list(asset: Option[Asset], page: Int = 0, pageSize: Int = 10, sort: String = "DESC", filter: String = ""): Page[AssetLog] = inTransaction {
    val offset = pageSize * page
    val asset_id = asset.map(_.getId)
    val query = from(tableDef)(a =>
      where(whereClause(a, asset_id, filter))
      select(a)
      orderBy(a.id.withSort(sort))
    )
    val paginatedQuery = optionallyPaginate(query, offset, pageSize)
    val results = paginatedQuery.toList
    val totalCount = from(tableDef)(a =>
      where(whereClause(a, asset_id, filter))
      compute(count)
    )
    Page(results, page, offset, totalCount)
  }

  def findByAsset(asset: Asset) = inTransaction {
    from(tableDef)(a =>
      where(a.asset_id === asset.id)
      select(a)
    ).toList
  }

  private def whereClause(a: AssetLog, asset_id: Option[Long], filter: String) = {
    filter match {
      case e if e.isEmpty =>
        (a.asset_id === asset_id.?)
      case ne =>
        (a.asset_id === asset_id.?) and
        filterToClause(ne, a)
    }
  }

  private def filterToClause(filter: String, log: AssetLog): LogicalBoolean = {
    val (negated, filters) = filter.split(';').foldLeft(false,Set[LogMessageType]()) { case(tuple, fs) => 
      val negate = fs.startsWith("!")
      val mt = negate match {
        case true =>
          LogMessageType.withName(fs.drop(1).toUpperCase)
        case false =>
          LogMessageType.withName(fs.toUpperCase)
      }
      if (negate || tuple._1) {
        (true, tuple._2 ++ Set(mt))
      } else {
        (false, tuple._2 ++ Set(mt))
      }
    }
    if (negated) {
      (log.message_type notIn filters)
    } else {
      (log.message_type in filters)
    }
  }

}
