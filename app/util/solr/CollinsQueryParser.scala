package util.plugins.solr

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
      Left("Unexpected stuff after query at position %s: %s".format(next.pos.toString, next.first))
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

  //def inKv          = ident ~ "(?iu)IN".r ~> "[" ~>
  def rangeKv       = ident ~ "=" ~ "[" ~ valueOpt ~ "," ~ valueOpt <~ "]" ^^ {case key ~ "=" ~ "[" ~ low ~ "," ~ high => SolrKeyRange(key,low,high)}
  def kv            = ident ~ "=" ~ value ^^{case k ~ "=" ~ v => SolrKeyVal(k,v)}
  def valueOpt: Parser[Option[SolrSingleValue]]      = "*"^^^{None} | value ^^{other => Some(other)}
  def value         = booleanValue | ipAddress | numberValue | stringValue
  def numberValue   = decimalNumber ^^{case n => if (n contains ".") {
    SolrDoubleValue(java.lang.Double.parseDouble(n))
  } else {
    SolrIntValue(java.lang.Integer.parseInt(n))
  }}
  def ipAddress  = """^(\*|[0-9]{1,3}\.(\*|[0-9]{1,3}\.(\*|[0-9]{1,3}\.(\*|[0-9]{1,3}))))$""".r ^^{s => StringValueFormat.createValueFor(s)}
  def stringValue   = quotedString | unquotedString
  def quotedString = stringLiteral  ^^ {s => SolrStringValue(s.substring(1,s.length-1))}
  def unquotedString = "\\^?\\*?[a-zA-Z0-9_\\-.]+\\*?\\$?".r  ^^ {s => StringValueFormat.createValueFor(s)}
  def booleanValue  = ("true" | "false") ^^ {case "true" => SolrBooleanValue(true) case _ =>  SolrBooleanValue(false)}

}
