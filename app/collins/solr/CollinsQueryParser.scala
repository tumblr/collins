package collins.solr

import play.api.PlayException

import scala.util.parsing.combinator._

import Solr.AssetSolrDocument

class CollinsQueryException(m: String) extends PlayException("CQL", m)

/** 
 * Parses CQL strings into a SolrExpression AST
 */
class CollinsQueryParser extends JavaTokenParsers {

  def parseQuery(input: String): Either[String, SolrExpression] = parse(topExpr, input.trim) match {
    case Success(exp, next) => if (next.atEnd) {
      Right(exp)
    } else {
      Left("Unexpected stuff after query at position %s: %s, parsed %s".format(next.pos.toString, next.first, exp.toString))
    }
    case Failure(wtf, _) => Left("Error parsing query: %s".format(wtf.toString))
  }

  def topExpr = emptyExpr | expr

  def emptyExpr = "*" ^^^{EmptySolrQuery}
  def expr: Parser[SolrExpression] = orOp

  def orOp          = rep1sep(andOp , "(?iu)OR".r) ^^ {i => if (i.tail == Nil) i.head else SolrOrOp(i)}
  def andOp         = rep1sep(simpleExpr , "(?iu)AND".r)  ^^ {i => if (i.tail == Nil) i.head else SolrAndOp(i)}
  def notExpr       = "(?iu)NOT".r ~> simpleExpr ^^ {e => SolrNotOp(e)}
  def simpleExpr:Parser[SolrExpression]    = notExpr | rangeKv | kv | "(" ~> expr <~ ")" 

  //range values are slightly different from regular values, since we cannot
  //allow quoted strings or strings with regexes
  def rangeKv       = ident ~ "=" ~ "[" ~ rangeValueOpt ~ "," ~ rangeValueOpt <~ "]" ^^ {case key ~ "=" ~ "[" ~ low ~ "," ~ high => SolrKeyRange(key,low,high)}
  def rangeValueOpt: Parser[Option[SolrSingleValue]]      = "*"^^^{None} | rangeValue ^^{other => Some(other)}
  def rangeValue    = strictUnquotedStringValue
  def strictUnquotedStringValue = "[a-zA-Z0-9_\\-.]+".r ^^{s => SolrStringValue(s, StrictUnquoted)}

  /**
   * Notice that all values are parsed as strings, type inference is now
   * handled in the typeCheck phase.  This is because in some cases we need to
   * parse numbers as strings, particularly for numeric asset tags.
   */
  def kv            = ident ~ "=" ~ value ^^{case k ~ "=" ~ v => SolrKeyVal(k,v)}
  def value   = quotedString | unquotedString
  def quotedString = stringLiteral  ^^ {s => SolrStringValue(s.substring(1,s.length-1), Quoted)}
  def unquotedString = """[^\s()'"]+""".r  ^^ {s => StringValueFormat.createValueFor(s)}

}
