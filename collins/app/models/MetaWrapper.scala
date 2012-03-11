package models

import util.Cache

import anorm._
import anorm.SqlParser._
import conversions._
import java.sql.Connection
import java.util.Date

/**
 * Provide a convenience wrapper on top of a row of meta/value data
 */
case class MetaWrapper(_meta: AssetMeta, _value: AssetMetaValue) {
  def getAssetId(): Long = _value.asset_id.id
  def getMetaId(): Long = _meta.getId
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
  def createMeta(asset: Asset, metas: Map[String,String])(implicit con: Connection) = {
    val metaValues = metas.map { case(k,v) =>
      val metaName = k.toUpperCase
      val meta: AssetMeta = AssetMeta.findByName(metaName, con).getOrElse {
        AssetMeta.create(AssetMeta(metaName, -1, metaName.toLowerCase.capitalize, metaName))
      }
      Cache.invalidate("MetaWrapper(%d).getMetaAttribute(%s)(1)".format(asset.getId, metaName))
      AssetMetaValue(asset, meta.id, v)
    }.toSeq
    AssetMetaValue.purge(metaValues)
    val values = metaValues.filter(v => v.value != null && v.value.nonEmpty)
    values.size match {
      case 0 =>
      case n =>
        AssetMetaValue.create(values)
        Asset.update(asset.copy(updated = Some(new Date().asTimestamp)))
    }
  }

  def findMeta(asset: Asset, name: String, count: Int = 1): Seq[MetaWrapper] = {
    Cache.getOrElseUpdate("MetaWrapper(%d).getMetaAttribute(%s)(%d)".format(asset.getId, name.toUpperCase, count)) {
      Model.withConnection { implicit con =>
        AssetMeta.findByName(name, con).map { meta =>
          val values = AssetMetaValue.find(
            "asset_id={asset_id} AND asset_meta_id={asset_meta_id} LIMIT %d".format(count)
          ).on(
            'asset_id -> asset.getId,
            'asset_meta_id -> meta.getId
          ).list()
          values.map { amv => MetaWrapper(meta, amv) }
        }.getOrElse(Nil)
      }
    }
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
      val finderQuery = afinder.asQueryFragment()
      val finderQueryFrag = finderQuery.sql.query match {
        case empty if empty.isEmpty => "1=1"
        case notEmpty => notEmpty
      }
      val paramsNoPaging = finderQuery.params
      val paramsWithPaging = Seq(
        ('pageSize -> toParameterValue(page.size)),
        ('offset -> toParameterValue(page.offset))
      ) ++ paramsNoPaging
      Model.withConnection { implicit con =>
        val assets = Asset.find(
          "%s limit {pageSize} offset {offset}".format(finderQueryFrag)
        ).on(paramsWithPaging:_*).list()
        val count = Asset.count("%s".format(finderQueryFrag)).on(paramsNoPaging:_*).as(scalar[Long])
        Page(assets, page.page, page.offset, count)
      }
    }
  }

  def findAssetsByMeta(page: PageParams, params: Seq[Tuple2[AssetMeta, String]], afinder: AssetFinder, operation: Option[String] = None): Page[Asset] = {
    val assetQuery = afinder.asQueryFragment()
    val assetQueryFragment = assetQuery.sql.query match {
      case empty if empty.isEmpty => ""
      case nonEmpty => nonEmpty + " and "
    }

    val metaQuery = collectParams(params, operation)

    val subQuery = """
      select %s from asset_meta_value amv
      where %s""".format("distinct asset_id", metaQuery.sql.query)

    val paramsWithoutPaging = assetQuery.params ++ metaQuery.params
    val allQueryParams = Seq(
      ('pageSize -> toParameterValue(page.size)),
      ('offset -> toParameterValue(page.offset))
    ) ++ paramsWithoutPaging

    Model.withConnection { implicit con =>
      val assets = Asset.find(
        "%s id in (%s) limit {pageSize} offset {offset}".format(
          assetQueryFragment,
          subQuery
        )
      ).on(allQueryParams:_*).list()
      val count = Asset.count(
        "%s id in (%s)".format(
          assetQueryFragment,
          subQuery
        )
      ).on(paramsWithoutPaging:_*).as(scalar[Long])
      Page(assets, page.page, page.offset, count)
    }
  }

  sealed trait QueryType {
    val sql: SimpleSql[Row]
  }
  case class ExcludeQuery(sql: SimpleSql[Row]) extends QueryType
  case class IncludeQuery(sql: SimpleSql[Row]) extends QueryType
  // This generates a subquery that looks roughly like
  //  ((value1 LIKE thing) OR (value2 LIKE other)...) AND ((value3 NOT EXISTS) AND (value4 NOT EXISTS))
  //  This creates a query that finds assets that match specific values, and is missing certain
  //  other attributes. This is not straight forward code.
  private[this] def collectParams(assetMeta: Seq[Tuple2[AssetMeta, String]], operation: Option[String]): SimpleSql[Row] = {
    val (isAnd, andOrString) = operation.map(_.trim.toLowerCase).map {
      case "and" => (true, " and ")
      case _ => (false, " or ")
    }.getOrElse((false," or "))
    val result: Seq[QueryType] = assetMeta.zipWithIndex.map { case(tuple, size) =>
      val metaName = "asset_meta_id_%d".format(size) // Name for query expansion
      val metaValueName = "asset_meta_value_value_%d".format(size) // value for query expansion
      val metaId = tuple._1.getId // meta id of thing we're querying for
      val initValue = tuple._2 // initial value that we're looking for, associated with metaId
      if (initValue.isEmpty) { // if it's empty, generate a sub-query that excludes these assets
        val filterQuery = """
        (select CASE count(*) WHEN 0 THEN 1 ELSE 0 END from asset_meta_value amv2 where amv2.asset_id = amv.asset_id
        and amv2.asset_meta_id={%s})
        """.format(metaName) // if count is positive 0 (exclude this asset), otherwise 1.
        ExcludeQuery(SqlQuery(filterQuery).on(metaName -> metaId))
      } else {
        val regexValue = regexWrap(initValue) // regex to use for searching
        val includeQuery = if (isAnd) {
          """
          (select count(*) from asset_meta_value amv2 where amv2.asset_id = amv.asset_id
            and amv2.asset_meta_id={%s} AND amv2.value REGEXP {%s})
          """.format(metaName, metaValueName)
        } else {
          "(amv.asset_meta_id={%s} AND amv.value REGEXP {%s})".format(metaName, metaValueName)
        }
        val simpleQuery = SqlQuery(includeQuery).on(metaName -> metaId, metaValueName -> regexValue)
        IncludeQuery(simpleQuery)
      }
    }
    val includes = result.filter(_.isInstanceOf[IncludeQuery]).map(_.sql)
    val excludes = result.filter(_.isInstanceOf[ExcludeQuery]).map(_.sql)
    // the and/or stuff should be configurable but it's not
    val includeSql = includes match {
      case Nil => None
      case rows => Some(DaoSupport.flattenSql(rows, andOrString))
    }
    val excludeSql = excludes match {
      case Nil => None
      case rows => Some(DaoSupport.flattenSql(rows, " and "))
    }
    DaoSupport.flattenSql(Seq(includeSql, excludeSql).filter(_.isDefined).map(_.get), " and ")
  }

  private[this] def regexWrap(s: String): String = {
    val prefixed = s.startsWith("^") match {
      case true => s
      case false => ".*" + s
    }
    s.endsWith("$") match {
      case true => prefixed
      case false => prefixed + ".*"
    }
  }
}


