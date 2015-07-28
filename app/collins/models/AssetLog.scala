package collins.models

import java.sql.Timestamp
import java.util.Date

import org.squeryl.Schema
import org.squeryl.annotations.Column
import org.squeryl.PrimitiveTypeMode._
import org.squeryl.Schema
import org.squeryl.annotations.Column
import org.squeryl.annotations.Transient
import org.squeryl.dsl.ast.LogicalBoolean

import play.api.libs.json.JsObject
import play.api.libs.json.JsString
import play.api.libs.json.Json

import collins.models.conversions.dateToTimestamp
import collins.models.conversions.orderByString2oba
import collins.models.shared.AnormAdapter
import collins.models.shared.Page
import collins.models.shared.ValidatedEntity
import collins.models.conversions.AssetLogFormat
import collins.models.logs.LogFormat
import collins.models.logs.LogFormat.LogFormat
import collins.models.logs.LogMessageType
import collins.models.logs.LogMessageType.LogMessageType
import collins.models.logs.LogSource
import collins.models.logs.LogSource.LogSource
import collins.models.shared.AnormAdapter
import collins.models.shared.Page
import collins.models.shared.ValidatedEntity

case class AssetLog(
  @Column("ASSET_ID") assetId: Long,
  created: Timestamp,
  @Column("CREATED_BY") createdBy: String,
  format: LogFormat = LogFormat.PlainText,
  source: LogSource = LogSource.Internal,
  @Column("MESSAGE_TYPE") messageType: LogMessageType = LogMessageType.Emergency,
  message: String,
  id: Long = 0) extends ValidatedEntity[Long]
{
  def this() = // 0 arg constructor since we have enums
    this(0,new Date().asTimestamp, "", LogFormat.PlainText, LogSource.Internal, LogMessageType.Emergency, "", 0)

  override def validate() {
    require(message != null && message.length > 0)
  }
  override def asJson: String = toJsValue.toString
  def toJsValue() = Json.toJson(this)

  def getFormat(): LogFormat = format
  def isJson(): Boolean = getFormat() == LogFormat.Json
  def isPlainText(): Boolean = getFormat() == LogFormat.PlainText

  def getSource(): LogSource = source
  def isInternalSource(): Boolean = getSource() == LogSource.Internal
  def isApiSource(): Boolean = getSource() == LogSource.Api
  def isUserSource(): Boolean = getSource() == LogSource.User
  def isSystemSource(): Boolean = getSource() == LogSource.System

  def isEmergency(): Boolean =
    messageType == LogMessageType.Emergency
  def isAlert(): Boolean =
    messageType == LogMessageType.Alert
  def isCritical(): Boolean =
    messageType == LogMessageType.Critical
  def isError(): Boolean =
    messageType == LogMessageType.Error
  def isWarning(): Boolean =
    messageType == LogMessageType.Warning
  def isNotice(): Boolean =
    messageType == LogMessageType.Notice
  def isInformational(): Boolean =
    messageType == LogMessageType.Informational
  def isDebug(): Boolean =
    messageType == LogMessageType.Debug

  @Transient
  lazy val assetTag: String = asset.tag
  @Transient
  lazy val asset: Asset = Asset.findById(assetId).get

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

object AssetLog extends Schema with AnormAdapter[AssetLog] with AssetLogKeys {

  override val createEventName = Some("asset_log_create")

  override val tableDef = table[AssetLog]("asset_log")
  on(tableDef)(a => declare(
    a.id is(autoIncremented,primaryKey),
    columns(a.assetId, a.messageType) are(indexed)
  ))

  override def delete(t: AssetLog): Int = 0

  def apply(asset: Asset, createdBy: String, message: String, format: LogFormat, source: LogSource, mt: LogMessageType) = {
    new AssetLog(asset.id, new Date().asTimestamp, createdBy, format, source, mt, message)
  }

  // A "panic" condition - notify all tech staff on call? (earthquake? tornado?) - affects multiple
  // apps/servers/sites...
  def emergency(asset: Asset, createdBy: String, message: String, format: LogFormat, source: LogSource) = {
      apply(asset, createdBy, message, format, source, LogMessageType.Emergency)
  }

  // Should be corrected immediately, but indicates failure in a primary system - fix CRITICAL
  // problems before ALERT - example is loss of primary ISP connection
  def critical(asset: Asset, createdBy: String, message: String, format: LogFormat, source: LogSource) = {
      apply(asset, createdBy, message, format, source, LogMessageType.Critical)
  }

  // Should be corrected immediately - notify staff who can fix the problem - example is loss of
  // backup ISP connection
  def alert(asset: Asset, createdBy: String, message: String, format: LogFormat, source: LogSource) = {
      apply(asset, createdBy, message, format, source, LogMessageType.Alert)
  }

  // Non-urgent failures - these should be relayed to developers or admins; each item must be
  // resolved within a given time
  def error(asset: Asset, createdBy: String, message: String, format: LogFormat, source: LogSource) = {
      apply(asset, createdBy, message, format, source, LogMessageType.Error)
  }

  // Warning messages - not an error, but indication that an error will occur if action is not
  // taken, e.g. file system 85% full - each item must be resolved within a given time
  def warning(asset: Asset, createdBy: String, message: String, format: LogFormat, source: LogSource) = {
      apply(asset, createdBy, message, format, source, LogMessageType.Warning)
  }

  // Events that are unusual but not error conditions - might be summarized in an email to
  // developers or admins to spot potential problems - no immediate action required
  def notice(asset: Asset, createdBy: String, message: String, format: LogFormat, source: LogSource) = {
      apply(asset, createdBy, message, format, source, LogMessageType.Notice)
  }

  def note(asset: Asset, createdBy: String, message: String, format: LogFormat, source: LogSource) = {
      apply(asset, createdBy, message, format, source, LogMessageType.Note)
  }

  // Normal operational messages - may be harvested for reporting, measuring throughput, etc - no
  // action required
  def informational(asset: Asset, createdBy: String, message: String, format: LogFormat, source: LogSource) = {
      apply(asset, createdBy, message, format, source, LogMessageType.Informational)
  }

  //Info useful to developers for debugging the application, not useful during operations
  def debug(asset: Asset, createdBy: String, message: String, format: LogFormat, source: LogSource) = {
      apply(asset, createdBy, message, format, source, LogMessageType.Debug)
  }

  override def get(log: AssetLog) = inTransaction {
    tableDef.lookup(log.id).get
  }

  def list(asset: Option[Asset], page: Int = 0, pageSize: Int = 10, sort: String = "DESC", filter: String = ""): Page[AssetLog] = inTransaction {
    val offset = pageSize * page
    val asset_id = asset.map(_.id)
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
      where(a.assetId === asset.id)
      select(a)
    ).toList
  }

  def findById(id: Long): Option[AssetLog] = inTransaction {
    from(tableDef)(a =>
      where(a.id === id)
      select(a)
    ).toList.headOption
  }

  def findByIds(id: Seq[Long]): List[AssetLog] = inTransaction {
    from(tableDef)(s =>
      where(s.id in id)
      select(s)
    ).toList
  }

  private def whereClause(a: AssetLog, asset_id: Option[Long], filter: String) = {
    filter match {
      case e if e.isEmpty =>
        (a.assetId === asset_id.?)
      case ne =>
        (a.assetId === asset_id.?) and
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
      (log.messageType notIn filters)
    } else {
      (log.messageType in filters)
    }
  }

}
