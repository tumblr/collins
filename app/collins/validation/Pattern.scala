package collins.validation

object Pattern {
  val AlphaNumericR = """[A-Za-z0-9\-_]+""".r.pattern.matcher(_)

  def isAlphaNumericString(input: String): Boolean = {
    isNonEmptyString(input) && AlphaNumericR(input).matches
  }
  def isNonEmptyString(input: String): Boolean = {
    input != null && input.nonEmpty
  }
}
