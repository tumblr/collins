package models

import util.Cache

import conversions._
import java.util.Date
import org.squeryl.Query
import org.squeryl.dsl.ast.{BinaryOperatorNodeLogicalBoolean, ExistsExpression, ExpressionNode, LogicalBoolean}

/**
 * Provide a convenience wrapper on top of a row of meta/value data
 */
case class MetaWrapper(_meta: AssetMeta, _value: AssetMetaValue) {
  def getAssetId(): Long = _value.asset_id
  def getMetaId(): Long = _meta.id
  def getId(): (Long,Long) = (getAssetId(), getMetaId())
  def getName(): String = _meta.name
  def getGroupId(): Int = _value.group_id
  def getNameEnum(): Option[AssetMeta.Enum] = try {
    Some(AssetMeta.Enum.withName(getName()))
  } catch { case _ => None }
  def getPriority(): Int = _meta.priority
  def getLabel(): String = _meta.label
  def getDescription(): String = _meta.description
  def getValue(): String = _value.value
  override def toString(): String = getValue()
}
object MetaWrapper {
  import org.squeryl.PrimitiveTypeMode._

  def createMeta(asset: Asset, metas: Map[String,String]) = {
    val metaValues = metas.map { case(k,v) =>
      val metaName = k.toUpperCase
      val meta: AssetMeta = AssetMeta.findByName(metaName).getOrElse {
        AssetMeta.create(AssetMeta(metaName, -1, metaName.toLowerCase.capitalize, metaName))
        AssetMeta.findByName(metaName).get
      }
      AssetMetaValue(asset, meta.id, v)
    }.toSeq
    AssetMetaValue.purge(metaValues)
    val values = metaValues.filter(v => v.value != null && v.value.nonEmpty)
    values.size match {
      case 0 =>
      case n =>
        AssetMetaValue.create(values)
    }
  }

  def findMeta(asset: Asset, name: String, count: Int = 1): Seq[MetaWrapper] = {
    AssetMeta.findByName(name).map { meta =>
      AssetMetaValue.findByAssetAndMeta(asset, meta, count)
    }.getOrElse(Nil)
  }

  def findMeta(asset: Asset, name: String): Option[MetaWrapper] = {
    findMeta(asset, name, 1) match {
      case Nil => None
      case head :: Nil => Some(head)
    }
  }

  def findAssets(page: PageParams, params: util.AttributeResolver.ResultTuple, afinder: AssetFinder, operation: Option[String] = None): Page[Asset] = {
    if (params._1.nonEmpty) {
      IpmiInfo.findAssetsByIpmi(page, params._1, afinder)
    } else if (params._2.nonEmpty) {
      MetaWrapper.findAssetsByMeta(page, params._2, afinder, operation)
    } else {
      Asset.find(page, afinder)
    }
  }

  /**
   * select distinct asset_id from amv where
   *  ((amv.asset_meta_id like foo(0).id and amv.value like foo(0).value) and/or
   *   (amv.asset_meta_id like foo(1).id and amv.value like foo(1).value)) and
   *    ...
   *  (amv.asset_meta_id not in (amvs))
   */
  type AssetMetaFinder = Seq[Tuple2[AssetMeta, String]]
  def findAssetsByMeta(page: PageParams, toFind: AssetMetaFinder, afinder: AssetFinder, operation: Option[String]): Page[Asset] = {
    Model.withSqueryl {
    val whereClause = {amv: AssetMetaValue =>
      val expressions: Seq[LogicalBoolean] = Seq(
        includes(amv, toFind, operation).map(exists(_)),
        excludes(amv, toFind, operation).map(notExists(_))
      ).filter(_ != None).map(_.get)
      expressions.reduceRight((a,b) => new BinaryOperatorNodeLogicalBoolean(a, b, "and"))
    }

    val assetIds: Set[Long] = from(AssetMetaValue.tableDef)(amv =>
      where(whereClause(amv))
      select(amv.asset_id)
      orderBy(amv.asset_id.withSort(page.sort))
    ).distinct.toSet
    val totalCount: Long = from(AssetMetaValue.tableDef)(amv =>
      where(whereClause(amv))
      compute(countDistinct(amv.asset_id))
    )
    val assets = Asset.find(assetIds)
    Page(assets, page.page, page.offset, totalCount)
    }
  }

  protected def excludes(amv: AssetMetaValue, toFind: AssetMetaFinder, bool: Option[String]): Option[Query[Long]] = {
    val clauses = toFind.filter(_._2.isEmpty) // Find empty values, our marker
    val whereClauses = clauses match {
      case Nil =>
        return None
      case list => list.map { case(am, v) =>
        (amv.asset_meta_id === am.id and amv.value.withPossibleRegex(v))
      }
    }
    val whereClause = whereClauses.reduceRight((a, b) =>
      new BinaryOperatorNodeLogicalBoolean(a, b, "and")
    )
    Some(from(AssetMetaValue.tableDef)(a =>
      where(whereClause)
      select(a.asset_id)
    ))
  }

  protected def includes(amv: AssetMetaValue, toFind: AssetMetaFinder, bool: Option[String]): Option[Query[Long]] = {
    val isAnd = (bool.toBinaryOperator == "and")
    val clauses = toFind.filter(_._2.nonEmpty)
    val whereClauses = clauses match {
      case Nil =>
        return None
      case list => list.map { case(am, v) =>
        if (isAnd) {
          (amv.asset_meta_id === am.id and amv.value.withPossibleRegex(v))
        } else {
          (amv.asset_meta_id === am.id and amv.value.withPossibleRegex(v))
        }
      }
    }
    val whereClause = whereClauses.reduceRight{(a, b) =>
      new BinaryOperatorNodeLogicalBoolean(a, b, bool.toBinaryOperator)
    }
    Some(from(AssetMetaValue.tableDef)(a =>
      where(whereClause)
      select(a.asset_id)
    ))
  }

}
