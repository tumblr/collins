package models

import collins.validation.StringUtil

object Truthy {

  val WeakFalsyStrings = Set("null", "undefined", "")
  val StrongFalsyStrings = Set("false", "0", "no")
  val AllFalsyStrings = StrongFalsyStrings ++ WeakFalsyStrings

  val TruthyStrings = Set("true", "1", "yes")

  case class TruthyException(msg: String) extends Exception(msg)
}

case class Truthy(input: String, strict: Boolean = false) {

  private[this] val sanitizedInput = StringUtil.trim(input).map(_.toLowerCase).getOrElse("invalid")

  import Truthy._

  def isTruthy(): Boolean = TruthyStrings.contains(sanitizedInput)
  def isFalsy(): Boolean = if (strict) {
    StrongFalsyStrings.contains(sanitizedInput)
  } else {
    AllFalsyStrings.contains(sanitizedInput)
  }

  def toBoolean(): Boolean = isTruthy

  override def toString(): String = toBoolean.toString

  if (strict && !isTruthy && !isFalsy) {
    throw new TruthyException("Value is neither truthy or falsy")
  }

}
