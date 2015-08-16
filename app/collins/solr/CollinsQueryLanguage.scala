package collins.solr

import collins.models.AssetMeta.ValueType
import collins.models.AssetMeta.ValueType.Boolean
import collins.models.AssetMeta.ValueType.Double
import collins.models.AssetMeta.ValueType.Integer
import collins.models.AssetMeta.ValueType.String
import collins.models.Truthy
import collins.solr.UpperCaseString.UppercaseString2String
import collins.solr.UpperCaseString.string2UpperCaseString

/**
 * The top-level object parsed from a CQL expression
 */
case class CQLQuery(select: SolrDocType, where: SolrExpression) {
  def typeCheck: Either[String,TypedSolrExpression] = where.typeCheck(select).right.map{expr=>
    val docKeyVal = new SolrKeyVal("DOC_TYPE", SolrStringValue(select.name, StrictUnquoted)) with TypedSolrExpression
    expr match {
      case TypedEmptySolrQuery => docKeyVal
      case _ => new SolrAndOp(Set(expr,docKeyVal )) with TypedSolrExpression
    }
  }
}

/**
 * Any class mixing in this trait is part of the CQL AST that must translate
 * itself into a Solr Query
 */
sealed trait SolrQueryComponent {
  protected[solr] def traverseQueryString(): String = traverseQueryString(true)
  protected[solr] def traverseQueryString(toplevel: Boolean): String
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

abstract class SolrSingleValue(val valueType: ValueType) extends SolrValue with SolrQueryComponent

case class SolrIntValue(value: Int) extends SolrSingleValue(Integer) {
  def traverseQueryString(toplevel: Boolean) = value.toString
}

case class SolrDoubleValue(value: Double) extends SolrSingleValue(Double) {
  def traverseQueryString(toplevel: Boolean) = value.toString
}


case class SolrStringValue(value: String, quoteFormat: StringValueFormat = Unquoted) extends SolrSingleValue(String) {
  def traverseQueryString(toplevel: Boolean) = quoteFormat.format(value)

  def quoted = copy(quoteFormat = Quoted)
  def unquoted = copy(quoteFormat = Unquoted)
  def strictUnquoted = copy(quoteFormat = StrictUnquoted)
  def lr = copy(quoteFormat = LRWildcard)
  def l = copy(quoteFormat = LWildcard)
  def r = copy(quoteFormat = RWildcard)

}

case class SolrBooleanValue(value: Boolean) extends SolrSingleValue(Boolean) {
  def traverseQueryString(toplevel: Boolean) = if (value) "true" else "false"

}

//note, we don't have to bother with checking the types of the contained values
//since that's implicitly handled by AssetMeta
case class SolrMultiValue(values: MultiSet[SolrSingleValue], valueType: ValueType) extends SolrValue {
  require (values.size > 0, "Cannot create empty multi-value")

  def +(v: SolrSingleValue) = this.copy(values = values + v)

  lazy val value = values.toSeq.map{_.value}.toArray


}

object SolrMultiValue {
  def apply(values: MultiSet[SolrSingleValue]): SolrMultiValue = SolrMultiValue(values, values.headOption.map{_.valueType}.getOrElse(String))
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
  protected[solr] def typeCheck(docType: SolrDocType): Either[String, TypedSolrExpression]
}

/**
 * This mixin ensures that expressions are type checked before they are sent into solr
 */
sealed trait TypedSolrExpression extends SolrExpression {
  //only type-checked expressions can be turned into solr queries
  def toSolrQueryString: String = traverseQueryString(true)
}

sealed trait _EmptySolrQuery extends SolrExpression{
  def typeCheck(t: SolrDocType) = Right(TypedEmptySolrQuery)
  def traverseQueryString(toplevel: Boolean) = "*:*"
}

case object EmptySolrQuery extends _EmptySolrQuery

case object TypedEmptySolrQuery extends _EmptySolrQuery with TypedSolrExpression

abstract class SolrMultiExpr(exprs: Set[SolrExpression], op: String) extends SolrExpression {
  require(exprs.size > 0, "Cannot create empty multi-expression")

  //create a typed instance of this object
  def create(exprs: Set[SolrExpression]): TypedSolrExpression

  //create a typed instance of the dual of this object (and creates or, vice versa)
  //NOTE -
  def createOp(exprs:Set[SolrExpression]): TypedSolrExpression

  def traverseQueryString(toplevel: Boolean) = {
    val e = exprs
      .map{_.traverseQueryString(false)}.mkString(" %s ".format(op))
    if (toplevel) e else "(%s)".format(e)
  }

  def typeCheck(t: SolrDocType) = {
    val r = exprs.map{_.typeCheck(t)}.foldLeft(Right(Set()): Either[String, Set[SolrExpression]]){(build, next) => build match {
      case l@Left(error) => l
      case Right(set) => next match {
        case Left(error) => Left(error)
        case Right(expr) => Right(set + expr)
      }
    }}
    //if all the members are NOT's, apply de morgans laws to avoid solr bug
    r.right.map{s =>
      val ops:Set[SolrExpression] = s.flatMap{
        case SolrNotOp(expr) => Some(expr)
        case _ => None
      }
      if (ops.size == s.size) {
        new SolrNotOp(createOp(ops)) with TypedSolrExpression
      } else {
        create(s)
      }
    }

  }

}

case class SolrAndOp(exprs: Set[SolrExpression]) extends SolrMultiExpr(exprs, "AND") {
  def AND(k: SolrExpression) = SolrAndOp(Set(this, k))

  def create(exprs: Set[SolrExpression]) = new SolrAndOp(exprs) with TypedSolrExpression
  def createOp(exprs: Set[SolrExpression]) = new SolrOrOp(exprs) with TypedSolrExpression


}

case class SolrOrOp(exprs: Set[SolrExpression]) extends SolrMultiExpr(exprs, "OR") {
  def OR(k: SolrExpression) = SolrOrOp(Set(this,k))

  def create(exprs: Set[SolrExpression]) = new SolrOrOp(exprs) with TypedSolrExpression
  def createOp(exprs: Set[SolrExpression]) = new SolrAndOp(exprs) with TypedSolrExpression

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
        case _: Throwable => e.lookupByName(stringValue)
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

  def traverseQueryString(toplevel: Boolean) = "-" + expr.traverseQueryString(false)

}

case class SolrKeyVal(key: String, value: SolrSingleValue) extends SolrSimpleExpr {

  def traverseQueryString(toplevel: Boolean) = key + ":" + value.traverseQueryString(false)

  def typeCheck(t: SolrDocType) = typeCheckValue(t, key, value).right.map{case (solrKey, cleanValue) => new SolrKeyVal(solrKey, cleanValue) with TypedSolrExpression}

}

case class SolrKeyRange(key: String, low: Option[SolrSingleValue], high: Option[SolrSingleValue], inclusive: Boolean) extends SolrSimpleExpr {

  def traverseQueryString(toplevel: Boolean) = {
    val l = low.map{_.traverseQueryString}.getOrElse("*")
    val h = high.map{_.traverseQueryString}.getOrElse("*")
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

