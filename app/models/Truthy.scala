package models

case class Truthy(input: String, strict: Boolean = false) {
  case class TruthyException(msg: String) extends Exception(msg)

  val FalsyStrings = {
    val s1 = Set("false", "0", "no")
    if (strict) {
      s1
    } else {
     s1 ++ Set("null","undefined","")
    }
  }
  val TruthyStrings = Set("true", "1", "yes")

  def isTruthy(): Boolean = TruthyStrings.contains(input)
  def isFalsy(): Boolean = FalsyStrings.contains(input)

  def toBoolean(): Boolean = isTruthy

  override def toString(): String = toBoolean.toString

  if (strict && !isTruthy && !isFalsy) {
    throw new TruthyException("Value is neither truthy or falsy")
  }

}
