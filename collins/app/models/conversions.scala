package models

import java.util.Date
import java.sql.Timestamp
import org.squeryl.dsl.{NonNumericalExpression, StringExpression}
import org.squeryl.dsl.ast.{BinaryOperatorNodeLogicalBoolean, ExpressionNode, LogicalBoolean, OrderByArg, TypedExpressionNode}

object conversions {
  implicit def dateToTimestamp(date: Date) = new DateToTimestamp(date)
  implicit def ops2bo(o: Option[String]) = new LogicalBooleanFromString(o)
  implicit def reOrLike[E <% StringExpression[String]](s: E) = new PossibleRegex(s)
  implicit def orderByString2oba[E <% TypedExpressionNode[_]](e: E) = new OrderByFromString(e)
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
      case false => ".*" + pattern.stripPrefix(".*")
    }
    pattern.endsWith("$") match {
      case true => prefixed
      case false => prefixed.stripSuffix(".*") + ".*"
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


