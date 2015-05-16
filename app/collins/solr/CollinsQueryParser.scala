package collins.solr

import scala.util.parsing.combinator.JavaTokenParsers

import play.api.PlayException

class CollinsQueryException(m: String) extends PlayException("CQL", m)

/** 
 * Parses CQL strings into a SolrExpression AST
 */
class CollinsQueryParser private(val docTypes: List[SolrDocType]) extends JavaTokenParsers {

  def parseQuery(input: String): Either[String, CQLQuery] = parse(topExpr, clean(input)) match {
    case Success((docType, exp), next) => if (next.atEnd) {
      docTypes.find{_.name == docType.toUpperCase} match {
        case Some(t) => Right(CQLQuery(t,exp))
        case None => Left("Invalid SELECT type " + docType)
      }
    } else {
      Left("Unexpected stuff after query at position %s: %s, parsed %s".format(next.pos.toString, next.first, exp.toString))
    }
    case Failure(wtf, _) => Left("Error parsing query: %s".format(wtf.toString))
    case Error(wtf, _) => Left("Error parsing query: %s".format(wtf.toString))
  }

  def clean(rawInput: String) = {
    val trim = rawInput.trim
    if (
      (trim.startsWith("\"") && trim.endsWith("\"")) ||
      (trim.startsWith("'") && trim.endsWith("'"))
    ) {
      trim.substring(1, trim.length-1) 
    } else {
      trim
    }
  }

  def topExpr: Parser[(String, SolrExpression)] = withSelect | withoutSelect
  def withSelect = "(?iu)SELECT".r ~> ident ~ "(?ui)WHERE".r ~ whereExpr ^^ {case docType ~ where ~ expr => (docType, expr)}
  def withoutSelect = whereExpr ^^ {case expr => (docTypes.head.name, expr)}

  def whereExpr = emptyExpr | expr

  def emptyExpr = "*" ^^^{EmptySolrQuery}
  def expr: Parser[SolrExpression] = orOp

  def orOp          = rep1sep(andOp , "(?iu)OR".r) ^^ {i => if (i.tail == Nil) i.head else SolrOrOp(i.toSet)}
  def andOp         = rep1sep(simpleExpr , "(?iu)AND".r)  ^^ {i => if (i.tail == Nil) i.head else SolrAndOp(i.toSet)}
  def notExpr       = "(?iu)NOT".r ~> simpleExpr ^^ {e => SolrNotOp(e)}
  def simpleExpr:Parser[SolrExpression]    = notExpr | rangeKv | kv | "(" ~> expr <~ ")" 

  //range values are slightly different from regular values, since we cannot
  //allow quoted strings or strings with regexes
  def rangeKv       = range | compareOp
  def range = ident ~ "=" ~ "\\[|\\(".r ~ rangeValueOpt ~ "," ~ rangeValueOpt ~ "\\]|\\)".r ^^ {case key ~ "=" ~ open ~ low ~ "," ~ high ~ close => (open, close) match {
    case ("(",")") => SolrKeyRange(key,low,high,false)
    case ("[","]") => SolrKeyRange(key,low,high,true)
    case ("[",")") => SolrKeyRange(key,None,high,false) AND SolrKeyRange(key,low,None,true)
    case ("(","]") => SolrKeyRange(key,None,high,true) AND SolrKeyRange(key,low,None,false)
  }}
  def compareOp = ident ~ "<=|>=|<|>".r ~ rangeValue ^^ { case key ~ op ~ value => op match {
    case "<" => SolrKeyRange(key, None, Some(value), false)
    case "<=" => SolrKeyRange(key, None, Some(value), true)
    case ">" => SolrKeyRange(key, Some(value), None, false)
    case ">=" => SolrKeyRange(key, Some(value), None, true)
  }}
  def rangeValueOpt: Parser[Option[SolrSingleValue]]      = "*"^^^{None} | rangeValue ^^{other => Some(other)}
  def rangeValue    = strictUnquotedStringValue
  def strictUnquotedStringValue = "[a-zA-Z0-9_\\-.:]+".r ^^{s => SolrStringValue(s, StrictUnquoted)}

  /**
   * Notice that all values are parsed as strings, type inference is now
   * handled in the typeCheck phase.  This is because in some cases we need to
   * parse numbers as strings, particularly for numeric asset tags.
   */
  def kv            = ident ~ "!=|=".r ~ value ^^{case k ~ op ~ v => if (op == "!=") SolrNotOp(SolrKeyVal(k,v)) else SolrKeyVal(k,v)}
  def value   = quotedString | unquotedString
  def quotedString = stringLiteral  ^^ {s => SolrStringValue(s.substring(1,s.length-1), Quoted)}
  def unquotedString = """[^\s()'"]+""".r  ^^ {s => StringValueFormat.createValueFor(s)}

}

object CollinsQueryParser {
  
  def apply(types: List[SolrDocType] = List(AssetDocType)) = new CollinsQueryParser(types)
}
