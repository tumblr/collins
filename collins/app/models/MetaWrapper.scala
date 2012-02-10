package models

import util.Cache

import anorm._
import anorm.SqlParser._
import java.sql.Connection

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
}
object MetaWrapper {
  def createMeta(asset: Asset, metas: Map[String,String])(implicit con: Connection) = {
    val metaValues = metas.map { case(k,v) =>
      val metaName = k.toUpperCase
      val meta: AssetMeta = AssetMeta.findByName(metaName, con).getOrElse {
        AssetMeta.create(AssetMeta(metaName, -1, metaName.toLowerCase.capitalize, metaName))
      }
      Cache.invalidate("MetaWrapper(%d).getMetaAttribute(%s)".format(asset.getId, metaName))
      AssetMetaValue(asset, meta.id.get, v)
    }.toSeq
    AssetMetaValue.purge(metaValues)
    AssetMetaValue.create(metaValues)
  }

  def findMeta(asset: Asset, name: String): Option[MetaWrapper] = {
    Cache.getOrElseUpdate("MetaWrapper(%d).getMetaAttribute(%s)".format(asset.getId, name.toUpperCase)) {
      Model.withConnection { implicit con =>
        AssetMeta.findByName(name, con).flatMap { meta =>
          val value = AssetMetaValue.find(
              "asset_id={asset_id} AND asset_meta_id={asset_meta_id} LIMIT 1"
            ).on(
              'asset_id -> asset.getId,
              'asset_meta_id -> meta.getId
            ).singleOption()
          value.map { amv => MetaWrapper(meta, amv) }
        }
      }
    }
  }

  def findAssets(page: PageParams, params: util.AttributeResolver.ResultTuple, afinder: AssetFinder): Page[Asset] = {
    if (params._1.nonEmpty) {
      IpmiInfo.findAssetsByIpmi(page, params._1, afinder)
    } else if (params._2.nonEmpty) {
      MetaWrapper.findAssetsByMeta(page, params._2, afinder)
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

  def findAssetsByMeta(page: PageParams, params: Seq[Tuple2[AssetMeta, String]], afinder: AssetFinder): Page[Asset] = {
    val simpleSql = collectParams(params)
    val queryPlaceholder = """
      select %s from asset_meta_value amv
      where %s
    """
    val finderQuery = afinder.asQueryFragment()
    val finderQueryFrag = finderQuery.sql.query match {
      case empty if empty.isEmpty => ""
      case nonEmpty => nonEmpty + " and "
    }
    val allFragmentsString = simpleSql.sql.query
    val query = queryPlaceholder.format("distinct asset_id", allFragmentsString)
    val countQuery = queryPlaceholder.format("count(distinct asset_id)", allFragmentsString)
    val paramsNoPaging = finderQuery.params ++ simpleSql.params
    val paramsWithPaging = Seq(
      ('pageSize -> toParameterValue(page.size)),
      ('offset -> toParameterValue(page.offset))
    ) ++ paramsNoPaging
    Model.withConnection { implicit con =>
      val assets = Asset.find("%s id in (%s) limit {pageSize} offset {offset}".format(finderQueryFrag, query)).on(paramsWithPaging:_*).list()
      val count = Asset.count("%s id in (%s)".format(finderQueryFrag, query)).on(paramsNoPaging:_*).as(scalar[Long])
      Page(assets, page.page, page.offset, count)
    }
  }

  private[this] def collectParams(assetMeta: Seq[Tuple2[AssetMeta, String]]): SimpleSql[Row] = {
    val result: Seq[SimpleSql[Row]] = assetMeta.zipWithIndex.map { case(tuple, size) =>
      val metaName = "asset_meta_id_%d".format(size)
      val metaValueName = "asset_meta_value_value_%d".format(size)
      val ivalue = tuple._2
      val nvalue = if (tuple._2.endsWith("$")) {
        ivalue
      } else {
        ivalue + ".*"
      }
      SqlQuery("(amv.asset_meta_id={%s} and amv.value REGEXP {%s})".format(
        metaName, metaValueName
      )).on(metaName -> tuple._1.getId, metaValueName -> nvalue)
    }
    DaoSupport.flattenSql(result, " or ")
  }
}


