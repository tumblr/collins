package collins.solr

import models.{Asset, AssetFinder, AssetMeta, AssetMetaValue, AssetType, IpAddresses, MetaWrapper, Page, PageParams, State, Status, Truthy}
import models.asset.AssetView
import models.IpmiInfo.Enum._


import Solr.AssetSolrDocument
import AssetMeta.ValueType
import AssetMeta.ValueType._

import play.api.Logger

sealed trait SolrDocType {
  def name: String
  def keyResolver: SolrKeyResolver
  
  lazy val keyVal = new SolrKeyVal(AllDocKeyResolver("DOC_TYPE").get.resolvedName, SolrStringValue(name)) with TypedSolrExpression
}
case object AssetDocType extends SolrDocType {
  val name = "ASSET"
  val keyResolver = AssetKeyResolver
}
case object AssetLogDocType extends SolrDocType {
  val name = "ASSET_LOG"
  val keyResolver = AssetLogKeyResolver
}


/**
 * The top-level object parsed from a CQL expression
 */
case class CQLQuery(select: SolrDocType, where: SolrExpression) {
  def typeCheck: Either[String,TypedSolrExpression] = where.typeCheck(select).right.map{expr=>
    new SolrAndOp(Set(expr, select.keyVal)) with TypedSolrExpression
  }    
}

/**
 * Any class mixing in this trait is part of the CQL AST that must translate
 * itself into a Solr Query
 */
sealed trait SolrQueryComponent {
  def toSolrQueryString(): String = toSolrQueryString(true)
  def toSolrQueryString(toplevel: Boolean): String
}

/**
 * Base trait of Solr Value ADT
 */
sealed trait SolrValue {
  val value: Any
  val valueType: ValueType

  //TODO: get rid of the implementation here and handle in sub-classes correctly
  def sortValue: String = value.toString

}

abstract class SolrSingleValue(val valueType: ValueType) extends SolrValue with SolrQueryComponent {
}

case class SolrIntValue(value: Int) extends SolrSingleValue(Integer) {
  def toSolrQueryString(toplevel: Boolean) = value.toString
}

case class SolrDoubleValue(value: Double) extends SolrSingleValue(Double) {
  def toSolrQueryString(toplevel: Boolean) = value.toString
}

/**
 * StringValueFormat determines how wildcards are used on string values.  We
 * currently allow leading and/or trailing wildcard characters on unquoted
 * string values.  If the value is quoted, such as foo = "*bar", the * is not
 * interpreted as a wildcard
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
//auto signifies whether the wildcards were specified by the user or
//automatically added
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
      SolrStringValue(rawStr.substring(strim, rawStr.length - (etrim)), format)
    } catch {
      case e =>
        logger.error("Error converting %s to SolrString: %s".format(rawStr, e.getMessage), e)
        throw e
    }
  }
}
  
   

/**
 * The string value is special becuase all values parsed from CQL are set as
 * strings, so this class must handle the string parsing into other value types
 */
case class SolrStringValue(value: String, quoteFormat: StringValueFormat = Unquoted) extends SolrSingleValue(String) {
  def toSolrQueryString(toplevel: Boolean) = quoteFormat.format(value)

  def quoted = copy(quoteFormat = Quoted)
  def unquoted = copy(quoteFormat = Unquoted)
  def strict = copy(quoteFormat = StrictUnquoted)
  def lr = copy(quoteFormat = LRWildcard)
  def l = copy(quoteFormat = LWildcard)
  def r = copy(quoteFormat = RWildcard)

}

case class SolrBooleanValue(value: Boolean) extends SolrSingleValue(Boolean) {
  def toSolrQueryString(toplevel: Boolean) = if (value) "true" else "false"

}

//note, we don't have to bother with checking the types of the contained values
//since that's implicitly handled by AssetMeta
case class SolrMultiValue(values: Set[SolrSingleValue], valueType: ValueType) extends SolrValue {
  require (values.size > 0, "Cannot create empty multi-value")

  def +(v: SolrSingleValue) = this.copy(values = values + v)

  lazy val value = values.map{_.value}.toArray

  
}

object SolrMultiValue {
  def apply(values: Set[SolrSingleValue]): SolrMultiValue = SolrMultiValue(values, values.headOption.map{_.valueType}.getOrElse(String))
}


/*
 * This is the AST and parser for CQL Expressions are basically just
 * primitive-recursive boolean expressions with a few extra operators for
 * various solr functions.  Some exmaples:
 *
 * key = "somevalue"
 * key1 = "a" AND (key2 = 3 OR key3 = true)
 */

/**
 * ADT for AST
 */
sealed trait SolrExpression extends SolrQueryComponent{ 

  /*
   * Performs type-checking and solr-key name resolution: eg converts foo to
   * foo_meta_i.  Returns Right(expr) with the new resolved expression or
   * Left(msg) if there's an error somewhere
   */
  def typeCheck(docType: SolrDocType): Either[String, TypedSolrExpression]
}

/**
 * This mixin ensures that expressions are type checked before they are sent into solr
 */
sealed trait TypedSolrExpression extends SolrExpression

case object EmptySolrQuery extends SolrQueryComponent with TypedSolrExpression{
  def toSolrQueryString(toplevel: Boolean) = "*:*"

  def typeCheck(t: SolrDocType) = Right(EmptySolrQuery)
}

abstract class SolrMultiExpr(exprs: Set[SolrExpression], op: String) extends SolrExpression {
  require(exprs.size > 0, "Cannot create empty multi-expression")

  def toSolrQueryString(toplevel: Boolean) = {
    val e = exprs.map{_.toSolrQueryString(false)}.mkString(" %s ".format(op))
    if (toplevel) e else "(%s)".format(e)
  }

  def create(exprs: Set[SolrExpression]): TypedSolrExpression

  def typeCheck(t: SolrDocType) = {
    val r = exprs.map{_.typeCheck(t)}.foldLeft(Right(Set()): Either[String, Set[SolrExpression]]){(build, next) => build match {
      case l@Left(error) => l
      case Right(set) => next match {
        case Left(error) => Left(error)
        case Right(expr) => Right(set + expr)
      }
    }}
    r.right.map{s => create(s)}
  }

}

case class SolrAndOp(exprs: Set[SolrExpression]) extends SolrMultiExpr(exprs, "AND") {
  def AND(k: SolrExpression) = SolrAndOp(Set(this, k))

  def create(exprs: Set[SolrExpression]) = new SolrAndOp(exprs) with TypedSolrExpression


}

case class SolrOrOp(exprs: Set[SolrExpression]) extends SolrMultiExpr(exprs, "OR") {
  def OR(k: SolrExpression) = SolrOrOp(Set(this,k))

  def create(exprs: Set[SolrExpression]) = new SolrOrOp(exprs) with TypedSolrExpression

}


trait SolrSimpleExpr extends SolrExpression {

  def AND(k: SolrExpression) = SolrAndOp(Set(this,k))
  def OR(k: SolrExpression) = SolrOrOp(Set(this ,k))

  def typeError(key: String, expected: ValueType, actual: ValueType) = 
    "Key %s expects type %s, got %s".format(key, expected.toString, actual.toString)


  type TypeEither = Either[String, (String, SolrSingleValue)]


  /**
   * returns Left(error) or Right(solr_key_name)
   */
  def typeCheckValue(docType: SolrDocType, key: String, value: SolrSingleValue):Either[String, (String, SolrSingleValue)] = docType.keyResolver(key) match {
    case Some(solrKey) => typeCheckValue(solrKey, value).right.map{cleanValue => (solrKey.resolvedName, cleanValue)}
    case None => Left("Unknown key \"%s\"".format(key))
  }

  def typeCheckValue(solrKey: SolrKey, value: SolrSingleValue): Either[String, SolrSingleValue] = solrKey match {
    case e: EnumKey => (value match {
      case SolrStringValue(stringValue, _) => try {
        e.lookupById(java.lang.Integer.parseInt(stringValue)) 
      } catch {
        case _ => e.lookupByName(stringValue)
      }      
      case SolrIntValue(id) => e.lookupById(id)
    }) match {
      case Some(v) => Right(SolrStringValue(v, StrictUnquoted))
      case None => Left("Invalid value %s for enum key %s".format(value.value.toString, solrKey.name))
    }
    case _ => value match {
      case s: SolrStringValue => {
        //string values are special because all values parsed from CQL are as strings
        def noRegexAllowed(f: => Either[String, SolrSingleValue]): Either[String, SolrSingleValue] = {
          if (Set[StringValueFormat](Quoted, Unquoted, StrictUnquoted) contains s.quoteFormat) {
            f
          } else {
            Left("Regex/wildcards not allowed on non-string values")
          }
        }
        solrKey.valueType match {
          case String => if (s.quoteFormat == Unquoted) {
            if (solrKey.autoWildcard) {
              Right(s.copy(quoteFormat = LRWildcard))
            } else {
              Right(s.copy(quoteFormat = Quoted))
            }
          } else {
            Right(s)
          }
          case Integer =>  noRegexAllowed( try {
            Right(SolrIntValue(java.lang.Integer.parseInt(s.value)))
          } catch {
            case _: NumberFormatException => Left("Invalid integer value '%s' for key %s".format(s.value, solrKey.name))
          })
          case Double => noRegexAllowed( try {
            Right(SolrDoubleValue(java.lang.Double.parseDouble(s.value)))
          } catch {
            case _: NumberFormatException => Left("Invalid double value '%s' for key %s".format(s.value, solrKey.name))
          })
          case Boolean => noRegexAllowed( try {
            Right(SolrBooleanValue((new Truthy(s.value)).isTruthy))
          } catch {
            case t: Truthy.TruthyException => Left(t.getMessage)
          })
        }
      }
      case _ => if (value.valueType == solrKey.valueType) {
        Right(value)
      } else {
        Left(typeError(solrKey.name, solrKey.valueType, value.valueType))
      }
    }
  }


}



case class SolrNotOp(expr: SolrExpression) extends SolrSimpleExpr {

  def typeCheck(t: SolrDocType) = expr.typeCheck(t).right.map{e => new SolrNotOp(e) with TypedSolrExpression}

  def toSolrQueryString(toplevel: Boolean) = "NOT " + expr.toSolrQueryString

}

case class SolrKeyVal(key: String, value: SolrSingleValue) extends SolrSimpleExpr {

  def toSolrQueryString(toplevel: Boolean) = key + ":" + value.toSolrQueryString(false)

  def typeCheck(t: SolrDocType) = typeCheckValue(t, key, value).right.map{case (solrKey, cleanValue) => new SolrKeyVal(solrKey, cleanValue) with TypedSolrExpression}

}

case class SolrKeyRange(key: String, low: Option[SolrSingleValue], high: Option[SolrSingleValue], inclusive: Boolean) extends SolrSimpleExpr {

  def toSolrQueryString(toplevel: Boolean) = {
    val l = low.map{_.toSolrQueryString}.getOrElse("*")
    val h = high.map{_.toSolrQueryString}.getOrElse("*")
    if (inclusive) {
      key + ":[" + l + " TO " + h + "]"
    } else {
      key + ":{" + l + " TO " + h + "}"
    }
  }


  /**
   * When type-checking ranges, we need to account for open ranges, meaning we
   * don't want to treat a missing value as an error
   */
  def typeCheck(t: SolrDocType) = t.keyResolver.either(key).right.flatMap{solrKey =>
    def check(v: Option[SolrSingleValue]): Either[String, Option[SolrSingleValue]] = v match {
      case None => Right(None)
      case Some(s) => typeCheckValue(solrKey, s).right.map{cleanValue => Some(cleanValue)}
    }
    (check(low), check(high)) match {
      case (Left(e), _) => Left(e)
      case (_, Left(e)) => Left(e)
      case (Right(l), Right(h)) => Right(new SolrKeyRange(solrKey.resolvedName, l,h, inclusive) with TypedSolrExpression)
    }
  }


}

