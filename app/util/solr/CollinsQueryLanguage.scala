package util.plugins.solr

import models.{Asset, AssetFinder, AssetMeta, AssetMetaValue, AssetType, IpAddresses, MetaWrapper, Page, PageParams, Status, Truthy}
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

case class SolrStringValue(value: String) extends SolrSingleValue(String) {
  def toSolrQueryString(toplevel: Boolean) = value
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
  val nonMetaKeys: Seq[SolrKey] = List(
    SolrKey("TAG", String,false), 
    SolrKey("CREATED", String,false), 
    SolrKey("UPDATE", String,false), 
    SolrKey("DELETED", String,false),
    SolrKey("IP_ADDRESS", String,false),
    SolrKey(IpmiAddress.toString, String, true),
    SolrKey(IpmiUsername.toString, String, true),
    SolrKey(IpmiPassword.toString, String, true),
    SolrKey(IpmiGateway.toString, String, true),
    SolrKey(IpmiNetmask.toString, String, true)
  ) ++ Solr.plugin.map{_.serializer.generatedFields}.getOrElse(List())

  val enumKeys = Map[SolrKey, String => Option[Int]](
    SolrKey("TYPE",Integer,false) -> ((s: String) => try Some(AssetType.Enum.withName(s.toUpperCase).id) catch {case _ => None}),
    SolrKey("STATUS",Integer,false) -> ((s: String) => Status.findByName(s).map{_.id})
  )

  def typeLeft(key: String, expected: ValueType, actual: ValueType): Either[String, (String, SolrSingleValue)] = 
    Left("Key %s expects type %s, got %s".format(key, expected.toString, actual.toString))


  type TypeEither = Either[String, (String, SolrSingleValue)]

  /**
   * returns Left(error) or Right(solr_key_name)
   */
  def typeCheckValue(key: String, value: SolrSingleValue):Either[String, (String, SolrSingleValue)] = {
    val ukey = key.toUpperCase
    val a: Option[TypeEither] = nonMetaKeys.find(_.name == ukey).map {solrKey =>
      if (solrKey.valueType == value.valueType) {
        Right(solrKey.resolvedName -> value)
      } else {
        typeLeft(key, solrKey.valueType, value.valueType)
      }
    } orElse{enumKeys.find(_._1.name == ukey).map{case(solrKey, valueResolver) => value match {
      case SolrStringValue(e) => valueResolver(e) match {
        case Some(i) => Right(solrKey.resolvedName -> SolrIntValue(i))
        case _ => Left("Invalid %s: %s".format(key, e))
      }
      case s:SolrIntValue => Right(solrKey.resolvedName -> value) : Either[String, (String, SolrSingleValue)]
      case other => typeLeft(key, String, other.valueType)
    }}}
    a.getOrElse(AssetMeta.findByName(key) match {
      case Some(meta) => if (meta.valueType == value.valueType) {
        //FIXME: perhaps centralize asset meta key formatting
        Right(meta.getSolrKey.resolvedName -> value)
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

