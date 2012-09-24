package util.plugins.solr

import models.{Asset, AssetFinder, AssetMeta, AssetMetaValue, AssetType, IpAddresses, MetaWrapper, Page, PageParams, State, Status, Truthy}
import models.asset.AssetView
import models.IpmiInfo.Enum._

import scala.util.parsing.combinator._

import Solr.AssetSolrDocument
import AssetMeta.ValueType
import AssetMeta.ValueType._

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

case object EmptySolrQuery extends SolrQueryComponent with SolrExpression{
  def toSolrQueryString(toplevel: Boolean) = "*:*"

  def typeCheck = Right(EmptySolrQuery)
}

/** 
 * This class holds data about a solr key, mainly for translating "local" key
 * names to their solr equivalent
 */
case class SolrKey (
  val name: String,
  val valueType: ValueType,
  val isDynamic: Boolean = true
) {
  lazy val resolvedName = name.toUpperCase + (if(isDynamic) ValueType.postFix(valueType) else "")
  def isAliasOf(alias: String) = false //override for aliases

  def matches(k: String) = (k == name) || isAliasOf(k)
}

/**
 * Mixin for enum keys, allows us to resolve the solr key and then validate
 * the enum value by passing it to the valueLookup method.
 */
trait KeyLookup{ self: SolrKey =>
  def lookupValue(value: String): Option[Int]
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

abstract class SolrSingleValue(val valueType: ValueType) extends SolrValue with SolrQueryComponent

case class SolrIntValue(value: Int) extends SolrSingleValue(Integer) {
  def toSolrQueryString(toplevel: Boolean) = value.toString
}

case class SolrDoubleValue(value: Double) extends SolrSingleValue(Double) {
  def toSolrQueryString(toplevel: Boolean) = value.toString
}

/**
 * StringValueFormat determines how wildcards are used on string values.  We currently allow leading and/or trailing wildcard characters on
 * unquoted string values.  If the value is quoted, such as foo = "*bar", the * is not interpreted as a wildcard
 */
sealed trait StringValueFormat {
  def format(value: String): String
}
case object LWildcard extends StringValueFormat {
  def format(value:String) = "*" + value
}
case object RWildcard extends StringValueFormat {
  def format(value: String) = value + "*"
}
case object LRWildcard extends StringValueFormat {
  def format(value: String) = "*" + value + "*"
}
case object Quoted extends StringValueFormat {
  def format(value: String) = "\"" + value + "\""
}
//no wildcard/regex allowed, mostly for range queries
case object StrictUnquoted extends StringValueFormat {
  def format(value: String) = value
}
case object FullWildcard extends StringValueFormat {
  def format(value: String) = if (value != "*") {
    throw new IllegalArgumentException("Cannot format non '*' string as full-wildcard")
  } else {
    "*"
  }
}

object StringValueFormat {

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
      (true, true, 0,0, LRWildcard)
    )
    val (_,_, strim, etrim, format) = states.find{x => x._1 && x._2}.get
    SolrStringValue(rawStr.substring(strim, rawStr.length - (etrim)), format)
  }
}
  
   

case class SolrStringValue(value: String, format: StringValueFormat = StrictUnquoted) extends SolrSingleValue(String) {
  def toSolrQueryString(toplevel: Boolean) = format.format(value)

  def quoted = copy(format = Quoted)
  def unquoted = copy(format = StrictUnquoted)
  
}

case class SolrBooleanValue(value: Boolean) extends SolrSingleValue(Boolean) {
  def toSolrQueryString(toplevel: Boolean) = if (value) "true" else "false"
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

object SolrKeyResolver {

  /**
   * each key is an "incoming" field from a query, the ValueType is the
   * expected type of the key, and the Boolean indicates whether the key in
   * Solr is static(false) or dynamic(true)
   *
   * NOTE - For now, any single-valued field that needs to be sortable has to be explicitly declared
   */
  lazy val nonMetaKeys: Seq[SolrKey] = List(
    SolrKey("TAG", String,false), 
    SolrKey("CREATED", String,false), 
    SolrKey("UPDATED", String,false), 
    SolrKey("DELETED", String,false),
    SolrKey("IP_ADDRESS", String,false),
    SolrKey("PRIMARY_ROLE", String,false),
    SolrKey("HOSTNAME", String,false),
    SolrKey(IpmiAddress.toString, String, true),
    SolrKey(IpmiUsername.toString, String, true),
    SolrKey(IpmiPassword.toString, String, true),
    SolrKey(IpmiGateway.toString, String, true),
    SolrKey(IpmiNetmask.toString, String, true)
  ) ++ Solr.plugin.map{_.serializer.generatedFields}.getOrElse(List())

  val typeKey = new SolrKey("TYPE",Integer,false) with KeyLookup {
    def lookupValue(value: String) = AssetType.findByName(value.toUpperCase).map(_.id)
    override def isAliasOf(a: String) = a == "ASSETTYPE"
  }

  val statusKey = new SolrKey("STATUS",Integer,false) with KeyLookup {
    def lookupValue(value: String) = Status.findByName(value).map{_.id}
  }

  val stateKey = new SolrKey("STATE", Integer, false) with KeyLookup {
    def lookupValue(value: String) = State.findByName(value).map{_.id}
  }

  val enumKeys = typeKey :: statusKey :: stateKey :: Nil

  def apply(_rawkey: String): Option[SolrKey] = {
    val ukey = _rawkey.toUpperCase
    nonMetaKeys.find(_ matches ukey)
      .orElse(enumKeys.find(_ matches ukey))
      .orElse(AssetMeta.findByName(ukey).map{_.getSolrKey})
  }

  def either(_rawkey: String) = apply(_rawkey) match {
    case Some(k) => Right(k)
    case None => Left("Unknown key " + _rawkey)
  }

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
      case SolrStringValue(stringValue, _) => j.lookupValue(stringValue) match {
        case Some(i) => Right(SolrIntValue(i))
        case _ => Left("Invalid %s: %s".format(solrKey.name, stringValue))
      }
      case s:SolrIntValue => Right(value)
      case other => Left(typeError(solrKey.name, String, other.valueType))
    }
    case _ => if (solrKey.valueType == value.valueType) {
      Right(value)
    } else {
      Left(typeError(solrKey.name, solrKey.valueType, value.valueType))
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

/**
 * needs some work, currently only a few tests use this.
 */
object CollinsQueryDSL {
  class CollinsQueryString(val s: String) {
    lazy val query: SolrExpression = (new CollinsQueryParser).parseQuery(s).fold(
      err => throw new Exception("CQL error: " + err),
      expr => expr
    )
  }
  implicit def str2SolrStringValue(s: String) = SolrStringValue(s)
  implicit def strsolr_tuple2keyval(t: Tuple2[String, SolrSingleValue]): SolrKeyVal = SolrKeyVal(t._1, t._2)
  implicit def str2collins(s: String): CollinsQueryString = new CollinsQueryString(s)
  implicit def collins2str(c: CollinsQueryString): String = c.s
  implicit def int_tuple2keyval(t: Tuple2[String, Int]):SolrKeyVal = SolrKeyVal(t._1, SolrIntValue(t._2))
  implicit def string_tuple2keyval(t: Tuple2[String, String]):SolrKeyVal = SolrKeyVal(t._1, SolrStringValue(t._2))
  implicit def double_tuple2keyval(t: Tuple2[String, Double]):SolrKeyVal = SolrKeyVal(t._1, SolrDoubleValue(t._2))
  implicit def boolean_tuple2keyval(t: Tuple2[String, Boolean]):SolrKeyVal = SolrKeyVal(t._1, SolrBooleanValue(t._2))

  def not(exp: SolrExpression) = SolrNotOp(exp)



}

