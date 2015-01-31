package collins.solr


import play.api.Logger


/**
 * StringValueFormat determines how wildcards are used on string values.  We
 * currently allow leading and/or trailing wildcard characters on unquoted
 * string values.  If the value is quoted, such as foo = "*bar", the * is not
 * interpreted as a wildcard
 *
 * We also support regex stard/end characters ^ and $
 */
sealed trait StringValueFormat {
  def addWildcards(value: String): String

  //if true, chars in charsToEscape will be escaped, eg : becomes \:
  //only needed for unquoted values
  def escapeChars: Boolean = true

  val charsToEscape = List(":")
  def addEscapeChars(value: String) = charsToEscape.foldLeft(value){(newValue, char) => newValue.replace(char, "\\" + char)}

  def format(value: String) = addWildcards(if (escapeChars) addEscapeChars(value) else value)
}
case object LWildcard extends StringValueFormat {
  def addWildcards(value:String) = "*" + value
}
case object RWildcard extends StringValueFormat {
  def addWildcards(value: String) = value + "*"
}
case object LRWildcard extends StringValueFormat {
  def addWildcards(value: String) = "*" + value + "*"
}
case object Quoted extends StringValueFormat {
  def addWildcards(value: String) = "\"" + value + "\""
  override val escapeChars = false
}

case object Unquoted extends StringValueFormat {
  //now you might be wondering, if this is unquoted, why are we quoting the
  //value?  This is because "unqouted" applies to the value as input through
  //CQL.  If the value is not padded with wildcards (which changes its format
  //to LRWildcard), then this should be searching for an exact match
  //
  //NOTE - this format is never actually sent to Solr, typechecking will
  //convert it into either LRWildcard or Quoted
  def addWildcards(value: String) = "\"" + value + "\""
}

//no wildcard/regex allowed
case object StrictUnquoted extends StringValueFormat {
  def addWildcards(value: String) = value
}
case object FullWildcard extends StringValueFormat {
  def addWildcards(value: String) = if (value != "*") {
    throw new IllegalArgumentException("Cannot addWildcards non '*' string as full-wildcard")
  } else {
    "*"
  }
}

object StringValueFormat {

  private[this] val logger = Logger("Solr-StringValueFormat")

  val fullWildcardValue = SolrStringValue("*", FullWildcard)

  /**
   * This is some pretty complex logic to handle user queries that include wildcards, 
   * regex markers ^,$, or some combination.  See the unit
   * tests for examples
   *
   * FIXME: make this more sane
   */
  def createValueFor(rawStr: String): SolrStringValue = if (rawStr == "*" || rawStr == ".*") fullWildcardValue else {
    def s(p: String) = rawStr.startsWith(p)
    def e(p: String) = rawStr.endsWith(p)
    val s_* = s("*")
    val e_* = e("*")
    val s__* = s(".*")
    val e__* = e(".*")
    val s_^ = s("^")
    val e_$ = e("$")
    val states = List(
      (s_*, e_$, 1, 1, LWildcard),
      (s__*, e__*, 2,2, LRWildcard),
      (s_*, e_*, 1, 1, LRWildcard),
      (s__*, e_$, 2, 1, LWildcard),
      (s_*, true, 1, 0, LWildcard),
      (s__*, true, 2, 0, LWildcard),
      (s_^, e__*, 1, 2, RWildcard),
      (s_^, e_*, 1, 1, RWildcard),
      (s_^, e_$, 1, 1, Quoted),
      (s_^, true, 1, 0, RWildcard),
      (true, e__*, 0, 2, RWildcard),
      (true, e_*, 0, 1, RWildcard),
      (true, e_$, 0, 1, LWildcard),
      (true, true, 0,0, Unquoted)
    )
    val (_,_, strim, etrim, format) = states.find{x => x._1 && x._2}.get
    try {
      SolrStringValue(rawStr.substring(strim, rawStr.length - (etrim)).trim(), format)
    } catch {
      case e: Throwable =>
        logger.error("Error converting %s to SolrString: %s".format(rawStr, e.getMessage), e)
        throw e
    }
  }
}
