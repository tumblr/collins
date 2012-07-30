package util.plugins.solr

import models.{Asset, AssetFinder, AssetMeta, AssetMetaValue, AssetType, AssetView, IpAddresses, MetaWrapper, Page, PageParams, Status, Truthy}
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
  val postfix: String
}

abstract class SolrSingleValue(val postfix: String, val valueType: ValueType) extends SolrValue with SolrQueryComponent

case class SolrIntValue(value: Int) extends SolrSingleValue("_meta_i", Integer) {
  def toSolrQueryString(toplevel: Boolean) = value.toString
}

case class SolrDoubleValue(value: Double) extends SolrSingleValue("_meta_d", Double) {
  def toSolrQueryString(toplevel: Boolean) = value.toString
}

case class SolrStringValue(value: String) extends SolrSingleValue("_meta_s", String) {
  def toSolrQueryString(toplevel: Boolean) = value
}

case class SolrBooleanValue(value: Boolean) extends SolrSingleValue("_meta_b", Boolean) {
  def toSolrQueryString(toplevel: Boolean) = if (value) "true" else "false"
}

//note, we don't have to bother with checking the types of the contained values
//since that's implicitly handled by AssetMeta
case class SolrMultiValue(values: Seq[SolrSingleValue], valueType: ValueType) extends SolrValue {
  require (values.size > 0, "Cannot create empty multi-value")

  def +(v: SolrSingleValue) = this.copy(values = values :+ v)

  lazy val value = values.map{_.value}.toArray

  lazy val postfix = values.head.postfix

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

  /**
   * each key is an "incoming" field from a query, the ValueType is the
   * expected type of the key, and the Boolean indicates whether the key in
   * Solr is static(false) or dynamic(true)
   */
  val nonMetaKeys: Map[String,(ValueType, Boolean)] = Map(
    "TAG" -> (String,false), 
    "CREATED" -> (String,false), 
    "UPDATE" -> (String,false), 
    "DELETED" -> (String,false),
    "IP_ADDRESS" -> (String,false),
    IpmiAddress.toString -> (String, true),
    IpmiUsername.toString -> (String, true),
    IpmiPassword.toString -> (String, true),
    IpmiGateway.toString -> (String, true),
    IpmiNetmask.toString -> (String, true)
  ) ++ Solr.plugin.map{_.serializer.generatedFields.map{case (k,v) => (k,(v,true))}}.getOrElse(Map())

  val enumKeys = Map[String, String => Option[Int]](
    "TYPE" -> ((s: String) => try Some(AssetType.Enum.withName(s.toUpperCase).id) catch {case _ => None}),
    "STATUS" -> ((s: String) => Status.findByName(s).map{_.id})
  )

  def typeLeft(key: String, expected: ValueType, actual: ValueType): Either[String, (String, SolrSingleValue)] = 
    Left("Key %s expects type %s, got %s".format(key, expected.toString, actual.toString))


  type TypeEither = Either[String, (String, SolrSingleValue)]

  /**
   * returns Left(error) or Right(solr_key_name)
   */
  def typeCheckValue(key: String, value: SolrSingleValue):Either[String, (String, SolrSingleValue)] = {
    val ukey = key.toUpperCase
    val a: Option[TypeEither] = nonMetaKeys.get(ukey).map {case (valueType, transformKey) =>
      if (valueType == value.valueType) {
        Right((if (transformKey) ukey + value.postfix else ukey) -> value)

      } else {
        typeLeft(key, valueType, value.valueType)
      }
    } orElse{enumKeys.get(ukey).map{f => value match {
      case SolrStringValue(e) => f(e) match {
        case Some(i) => Right(ukey -> SolrIntValue(i))
        case _ => Left("Invalid %s: %s".format(key, e))
      }
      case s:SolrIntValue => Right(ukey -> value) : Either[String, (String, SolrSingleValue)]
      case other => typeLeft(key, String, other.valueType)
    }}}
    a.getOrElse(AssetMeta.findByName(key) match {
      case Some(meta) => if (meta.valueType == value.valueType) {
        //FIXME: perhaps centralize asset meta key formatting
        Right(ukey + value.postfix -> value)
      } else {
        typeLeft(key, meta.valueType, value.valueType)
      }
      case None => Left("Unknown key \"%s\"".format(key))
    })
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

  def t(v: Option[SolrSingleValue]): Either[String, (String, Option[SolrSingleValue])] = v match {
    case None => Right(key, None)
    case Some(s) => typeCheckValue(key, s) match {
      case Left(e) => Left(e)
      case Right((k,v)) => Right((k,Some(v)))
    }
  }

  /**
   * When type-checking ranges, we need to account for open ranges, meaning we
   * don't want to treat a missing value as an error
   */
  def typeCheck = {
    (t(low), t(high)) match {
      case (Right((k,l)), Right((_,h))) => Right(SolrKeyRange(k, l, h))
      case (Left(e), _) => Left(e)
      case (_, Left(e)) => Left(e)
    }
  }


}

/**
 * needs some work, currently only a few tests use this.
 */
object CollinsQueryDSL {
  class CollinsQueryString(val s: String) {
    lazy val query: SolrExpression = (new CollinsQueryParser).parseQuery(s).right.get
  }
  implicit def str2collins(s: String): CollinsQueryString = new CollinsQueryString(s)
  implicit def collins2str(c: CollinsQueryString): String = c.s
  implicit def int_tuple2keyval(t: Tuple2[String, Int]):SolrKeyVal = SolrKeyVal(t._1, SolrIntValue(t._2))
  implicit def string_tuple2keyval(t: Tuple2[String, String]):SolrKeyVal = SolrKeyVal(t._1, SolrStringValue(t._2))
  implicit def double_tuple2keyval(t: Tuple2[String, Double]):SolrKeyVal = SolrKeyVal(t._1, SolrDoubleValue(t._2))
  implicit def boolean_tuple2keyval(t: Tuple2[String, Boolean]):SolrKeyVal = SolrKeyVal(t._1, SolrBooleanValue(t._2))

  def not(exp: SolrExpression) = SolrNotOp(exp)



}

