package collins.solr

import models.{Asset, AssetFinder, AssetMeta, AssetMetaValue, AssetType, IpAddresses, MetaWrapper, Page, PageParams, State, Status, Truthy}
import models.asset.AssetView
import models.IpmiInfo.Enum._


import Solr.AssetSolrDocument
import AssetMeta.ValueType
import AssetMeta.ValueType._

import play.api.Logger


/**
 * TODO: encode checked vs unchecked syntax trees into the type system to
 * prevent accidental executing of unchecked queries.
 */

/**
 * Any class mixing in this trait is part of the CQL AST that must translate
 * itself into a Solr Query
 *
 * This is a super-trait of SolrExpression becuase SolrValues are
 * QueryComponents, but should not require the typeCheck method of
 * SolrExpression (probably ways to refactor this but whatevah)
 */
sealed trait SolrQueryComponent {
  def toSolrQueryString(): String = toSolrQueryString(true)

  def toSolrQueryString(toplevel: Boolean): String

}




/**
 * Base trait of Solr Value ADT
 *
 * A solr value can either be a typed single value, or a multival containing a
 * seq of single values.  At the moment the Solr schema allows all meta values
 * to be multi-valued
 */
sealed trait SolrValue {
  val value: Any
  val valueType: ValueType

}

abstract class SolrSingleValue(val valueType: ValueType) extends SolrValue with SolrQueryComponent {
  /**
   * perform any validation or cleaning on the value, occurrs after the key is
   * verified to exist and the value is of the correct type.
   */
  def validateValue(key: SolrKey): Either[String, SolrSingleValue]
}

case class SolrIntValue(value: Int) extends SolrSingleValue(Integer) {
  def toSolrQueryString(toplevel: Boolean) = value.toString

  def validateValue(key: SolrKey) = Right(this)

}

case class SolrDoubleValue(value: Double) extends SolrSingleValue(Double) {
  def toSolrQueryString(toplevel: Boolean) = value.toString

  def validateValue(key: SolrKey) = Right(this)
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
case class SolrStringValue(value: String, format: StringValueFormat = Unquoted) extends SolrSingleValue(String) {
  def toSolrQueryString(toplevel: Boolean) = format.format(value)

  def quoted = copy(format = Quoted)
  def unquoted = copy(format = StrictUnquoted)
  def lr = copy(format = LRWildcard)

  private def noRegexAllowed(f: => Either[String, SolrSingleValue]): Either[String, SolrSingleValue] = {
    if (Set[StringValueFormat](Quoted, Unquoted, StrictUnquoted) contains format) {
      f
    } else {
      Left("Regex/wildcards not allowed on non-string values")
    }
  }

  def validateValue(key: SolrKey) = key.valueType match {
    case String => if (format == Unquoted) {
      if (key.autoWildcard) {
        Right(this.copy(format = LRWildcard))
      } else {
        Right(this.copy(format = Quoted))
      }
    } else {
      Right(this)
    }
    case Integer =>  noRegexAllowed( try {
      Right(SolrIntValue(java.lang.Integer.parseInt(value)))
    } catch {
      case _: NumberFormatException => Left("Invalid integer value '%s' for key %s".format(value, key.name))
    })
    case Double => noRegexAllowed( try {
      Right(SolrDoubleValue(java.lang.Double.parseDouble(value)))
    } catch {
      case _: NumberFormatException => Left("Invalid double value '%s' for key %s".format(value, key.name))
    })
    case Boolean => noRegexAllowed( try {
      Right(SolrBooleanValue((new Truthy(value)).isTruthy))
    } catch {
      case t: Truthy.TruthyException => Left(t.getMessage)
    })
  }
  
}

case class SolrBooleanValue(value: Boolean) extends SolrSingleValue(Boolean) {
  def toSolrQueryString(toplevel: Boolean) = if (value) "true" else "false"

  def validateValue(key: SolrKey) = Right(this)
  

}

//note, we don't have to bother with checking the types of the contained values
//since that's implicitly handled by AssetMeta
case class SolrMultiValue(values: Seq[SolrSingleValue], valueType: ValueType) extends SolrValue {
  require (values.size > 0, "Cannot create empty multi-value")

  def +(v: SolrSingleValue) = this.copy(values = values :+ v)

  lazy val value = values.map{_.value}.toArray

  
}


object SolrMultiValue {

  def apply(values: Seq[SolrSingleValue]): SolrMultiValue = SolrMultiValue(values, values.headOption.map{_.valueType}.getOrElse(String))

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
  def typeCheck: Either[String, SolrExpression]
}

case object EmptySolrQuery extends SolrQueryComponent with SolrExpression{
  def toSolrQueryString(toplevel: Boolean) = "*:*"

  def typeCheck = Right(EmptySolrQuery)
}

abstract class SolrMultiExpr(exprs: Seq[SolrExpression], op: String) extends SolrExpression {
  require(exprs.size > 0, "Cannot create empty multi-expression")

  def toSolrQueryString(toplevel: Boolean) = {
    val e = exprs.map{_.toSolrQueryString(false)}.mkString(" %s ".format(op))
    if (toplevel) e else "(%s)".format(e)
  }

  def create(exprs: Seq[SolrExpression]): SolrMultiExpr

  def typeCheck = {
    val r = exprs.map{_.typeCheck}.foldLeft(Right(Nil): Either[String, Seq[SolrExpression]]){(build, next) => build match {
      case l@Left(error) => l
      case Right(seq) => next match {
        case Left(error) => Left(error)
        case Right(expr) => Right(expr +: seq)
      }
    }}
    r.right.map{s => create(s)}
  }

}

case class SolrAndOp(exprs: Seq[SolrExpression]) extends SolrMultiExpr(exprs, "AND") {
  def AND(k: SolrExpression) = SolrAndOp(this :: k :: Nil)

  def create(exprs: Seq[SolrExpression]) = SolrAndOp(exprs)


}

case class SolrOrOp(exprs: Seq[SolrExpression]) extends SolrMultiExpr(exprs, "OR") {
  def OR(k: SolrExpression) = SolrOrOp(this :: k :: Nil)

  def create(exprs: Seq[SolrExpression]) = SolrOrOp(exprs)

}


trait SolrSimpleExpr extends SolrExpression {

  def AND(k: SolrExpression) = SolrAndOp(this :: k :: Nil)
  def OR(k: SolrExpression) = SolrOrOp(this :: k :: Nil)

  def typeError(key: String, expected: ValueType, actual: ValueType) = 
    "Key %s expects type %s, got %s".format(key, expected.toString, actual.toString)


  type TypeEither = Either[String, (String, SolrSingleValue)]


  /**
   * returns Left(error) or Right(solr_key_name)
   */
  def typeCheckValue(key: String, value: SolrSingleValue):Either[String, (String, SolrSingleValue)] = SolrKeyResolver(key) match {
    case Some(solrKey) => typeCheckValue(solrKey, value).right.map{cleanValue => (solrKey.resolvedName, cleanValue)}
    case None => Left("Unknown key \"%s\"".format(key))
  }

  def typeCheckValue(solrKey: SolrKey, value: SolrSingleValue): Either[String, SolrSingleValue] = solrKey match {
    case j: KeyLookup => value match {
      case SolrStringValue(stringValue, _) => try Right(SolrIntValue(java.lang.Integer.parseInt(stringValue))) catch {case _ => j.lookupValue(stringValue) match {
        case Some(i) => Right(SolrIntValue(i))
        case _ => Left("Invalid %s: %s".format(solrKey.name, stringValue))
      }}
      case s:SolrIntValue => Right(value)
      case other => Left(typeError(solrKey.name, String, other.valueType))
    }
    case _ => {
      value
        .validateValue(solrKey)
        .right
        .flatMap{ cleanValue => if (solrKey.valueType == cleanValue.valueType) {
          Right(cleanValue)
        } else {
          Left(typeError(solrKey.name, solrKey.valueType, cleanValue.valueType))
        }}
    }
  }


}



case class SolrNotOp(expr: SolrExpression) extends SolrSimpleExpr {

  def typeCheck = expr.typeCheck.right.map{e => SolrNotOp(e)}

  def toSolrQueryString(toplevel: Boolean) = "NOT " + expr.toSolrQueryString

}

case class SolrKeyVal(key: String, value: SolrSingleValue) extends SolrSimpleExpr {

  def toSolrQueryString(toplevel: Boolean) = key + ":" + value.toSolrQueryString(false)

  def typeCheck = typeCheckValue(key, value).right.map{case (solrKey, cleanValue) => SolrKeyVal(solrKey, cleanValue)}

}

case class SolrKeyRange(key: String, low: Option[SolrSingleValue], high: Option[SolrSingleValue]) extends SolrSimpleExpr {

  def toSolrQueryString(toplevel: Boolean) = {
    val l = low.map{_.toSolrQueryString}.getOrElse("*")
    val h = high.map{_.toSolrQueryString}.getOrElse("*")
    key + ":[" + l + " TO " + h + "]"
  }

  def t(k: SolrKey, v: Option[SolrSingleValue]): Either[String, Option[SolrSingleValue]] = v match {
    case None => Right(None)
    case Some(s) => typeCheckValue(k, s).right.map{cleanValue => Some(cleanValue)}
  }

  /**
   * When type-checking ranges, we need to account for open ranges, meaning we
   * don't want to treat a missing value as an error
   */
  def typeCheck = SolrKeyResolver.either(key).right.flatMap{solrKey =>
    (t(solrKey,low), t(solrKey,high)) match {
      case (Left(e), _) => Left(e)
      case (_, Left(e)) => Left(e)
      case (Right(l), Right(h)) => Right(SolrKeyRange(solrKey.resolvedName, l,h))
    }
  }


}

