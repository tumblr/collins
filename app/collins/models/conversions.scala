package collins.models

import java.sql.Timestamp
import java.text.SimpleDateFormat
import java.util.Date

import org.squeryl.PrimitiveTypeMode._
import org.squeryl.dsl.StringExpression
import org.squeryl.dsl.ast.LogicalBoolean
import org.squeryl.dsl.ast.TypedExpressionNode

import play.api.libs.json.Format
import play.api.libs.json.JsObject
import play.api.libs.json.JsSuccess
import play.api.libs.json.JsValue
import play.api.libs.json.Json

import collins.util.IpAddress
import collins.util.views.Formatter.ISO_8601_FORMAT
import collins.util.views.Formatter.dateFormat

import collins.models.IpmiInfo.Enum.IpmiAddress
import collins.models.IpmiInfo.Enum.IpmiGateway
import collins.models.IpmiInfo.Enum.IpmiNetmask
import collins.models.IpmiInfo.Enum.IpmiPassword
import collins.models.IpmiInfo.Enum.IpmiUsername

object conversions {
  implicit def dateToTimestamp(date: Date) = new DateToTimestamp(date)
  implicit def ops2bo(o: Option[String]) = new LogicalBooleanFromString(o)
  implicit def reOrLike[E <% StringExpression[String]](s: E) = new PossibleRegex(s)
  implicit def orderByString2oba[E <% TypedExpressionNode[_]](e: E) = new OrderByFromString(e)
  implicit object TimestampFormat extends Format[Timestamp] {
    override def reads(json: JsValue) =
      JsSuccess(json.asOpt[String].filter(_.nonEmpty)
        .map { str =>
          val formatter = new SimpleDateFormat(ISO_8601_FORMAT)
          new Timestamp(formatter.parse(str).getTime)
        }.getOrElse {
          new Timestamp(0L)
        })
    override def writes(ts: Timestamp) = Json.toJson(dateFormat(ts))
  }
  implicit object IpmiFormat extends Format[IpmiInfo] {
    import IpmiInfo.Enum._
    override def reads(json: JsValue) = JsSuccess(IpmiInfo(
      (json \ "ASSET_ID").as[Long],
      (json \ IpmiUsername.toString).as[String],
      (json \ IpmiPassword.toString).as[String],
      IpAddress.toLong((json \ IpmiGateway.toString).as[String]),
      IpAddress.toLong((json \ IpmiAddress.toString).as[String]),
      IpAddress.toLong((json \ IpmiNetmask.toString).as[String]),
      (json \ "ID").asOpt[Long].getOrElse(0L)
    ))
    override def writes(ipmi: IpmiInfo) = JsObject(Seq(
      "ASSET_ID" -> Json.toJson(ipmi.asset_id),
      "ASSET_TAG" -> Json.toJson(Asset.findById(ipmi.asset_id).map(_.tag).getOrElse("Unknown")),
      IpmiUsername.toString -> Json.toJson(ipmi.username),
      IpmiPassword.toString -> Json.toJson(ipmi.password),
      IpmiGateway.toString -> Json.toJson(ipmi.dottedGateway),
      IpmiAddress.toString -> Json.toJson(ipmi.dottedAddress),
      IpmiNetmask.toString -> Json.toJson(ipmi.dottedNetmask),
      "ID" -> Json.toJson(ipmi.id)
    ))
  }
  implicit object IpAddressFormat extends Format[IpAddresses] {
    override def reads(json: JsValue) = JsSuccess(IpAddresses(
      (json \ "ASSET_ID").as[Long],
      IpAddress.toLong((json \ "GATEWAY").as[String]),
      IpAddress.toLong((json \ "ADDRESS").as[String]),
      IpAddress.toLong((json \ "NETMASK").as[String]),
      (json \ "POOL").asOpt[String].getOrElse(shared.IpAddressConfig.DefaultPoolName),
      (json \ "ID").asOpt[Long].getOrElse(0L)
    ))
    override def writes(ip: IpAddresses) = JsObject(Seq(
      "ASSET_ID" -> Json.toJson(ip.asset_id),
      "ASSET_TAG" -> Json.toJson(Asset.findById(ip.asset_id).map(_.tag).getOrElse("Unknown")),
      "GATEWAY" -> Json.toJson(ip.dottedGateway),
      "ADDRESS" -> Json.toJson(ip.dottedAddress),
      "NETMASK" -> Json.toJson(ip.dottedNetmask),
      "POOL" -> Json.toJson(ip.pool),
      "ID" -> Json.toJson(ip.id)
    ))
  }
  implicit object AssetLogFormat extends Format[AssetLog] {
    val df = new SimpleDateFormat("d/M/yy HH:mm:ss")
    override def reads(json: JsValue) = JsSuccess(AssetLog(
      (json \ "ASSET_ID").as[Long],
      (json \ "CREATED").as[Timestamp],
      (json \ "CREATED_BY").as[String],
      logs.LogFormat.withName((json \ "FORMAT").as[String]),
      logs.LogSource.withName((json \ "SOURCE").as[String]),
      logs.LogMessageType.withName((json \ "TYPE").as[String]),
      (json \ "MESSAGE").as[String],
      (json \ "ID").asOpt[Long].getOrElse(0L)
    ))
    override def writes(log: AssetLog) = JsObject(Seq(
      "ID" -> Json.toJson(log.id),
      "ASSET_TAG" -> Json.toJson(Asset.findById(log.asset_id).map(_.tag).getOrElse("Unknown")),
      "CREATED" -> Json.toJson(df.format(log.created)),
      "CREATED_BY" -> Json.toJson(log.created_by),
      "FORMAT" -> Json.toJson(log.format.toString),
      "SOURCE" -> Json.toJson(log.source.toString),
      "TYPE" -> Json.toJson(log.message_type.toString),
      "MESSAGE" -> (if (log.isJson()) {
        try {
          Json.parse(log.message)
        } catch {
          case e: Throwable => Json.toJson("Error parsing JSON: %s".format(e.getMessage))
        }
      } else {
        Json.toJson(log.message)
      })
    ))
  }
}

sealed private[models] class OrderByFromString(o: TypedExpressionNode[_]) {
  import org.squeryl.PrimitiveTypeMode._

  def withSort(s: String, default: String = "DESC") = {
    val sortOrder = Seq(s, default)
                      .map(_.toUpperCase.trim)
                      .find(s => s == "DESC" || s == "ASC")
                      .getOrElse("DESC")
    sortOrder match {
      case "DESC" => o desc
      case "ASC" => o asc
    }
  }
}
sealed private[models] class DateToTimestamp(date: Date) {
  def asTimestamp(): Timestamp = new Timestamp(date.getTime())
}

sealed private[models] class LogicalBooleanFromString(s: Option[String]) {
  def toBinaryOperator: String = toBinaryOperator()
  def toBinaryOperator(default: String = "or") = {
    s.orElse(Option(default)).map(_.toLowerCase.trim).get match {
      case "and" => "and"
      case _ => "or"
    }
  }
}

sealed private[models] class PossibleRegex(left: StringExpression[String]) {
  protected val RegexChars = List('[','\\','^','$','.','|','?','*','+','(',')')
  import org.squeryl.PrimitiveTypeMode._

  def withPossibleRegex(pattern: String): LogicalBoolean = {
    if (isExact(pattern)) {
      (left === withoutAnchors(pattern))
    } else if (isRegex(pattern)) {
      left.regex(wrapRegex(pattern))
    } else {
      left.like(wrapLike(pattern))
    }
  }

  protected def isRegex(pattern: String): Boolean = {
    RegexChars.find(pattern.contains(_)).map(_ => true).getOrElse(false)
  }
  protected def isExact(pattern: String): Boolean = {
    // exact match if the only regex that is specified are start and end anchors
    pattern.startsWith("^") && pattern.endsWith("$") && !isRegex(withoutAnchors(pattern))
  }
  protected def withoutAnchors(p: String) = p.stripPrefix("^").stripSuffix("$")

  protected def wrapLike(s: String): String = bookend("%", s, "%")
  protected def wrapRegex(pattern: String): String = {
    val prefixed = pattern.startsWith("^") match {
      case true => pattern
      case false => ".*" + pattern.stripPrefix(".*").stripPrefix("*")
    }
    pattern.endsWith("$") match {
      case true => prefixed
      case false => prefixed.stripSuffix(".*").stripSuffix("*") + ".*"
    }
  }

  // Bookend a string with start/end unless already starts with start/ends with end
  protected def bookend(prefix: String, s: String, suffix: String): String = {
    val withPrefix = Option(s.startsWith(prefix))
      .filter(_ == false)
      .map(_ => prefix + s)
      .getOrElse(s)
    Option(s.endsWith(suffix))
      .filter(_ == false)
      .map(_ => withPrefix + suffix)
      .getOrElse(withPrefix)
  }
}


