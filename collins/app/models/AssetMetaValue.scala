package models

import anorm._
import anorm.defaults._
import anorm.SqlParser._
import java.sql._

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

case class AssetMetaValue(asset_id: Id[java.lang.Long], asset_meta_id: Id[java.lang.Long], group_id: Int, value: String) {
  def getAsset(): Asset = {
    Asset.findById(asset_id.id).get
  }
  def getMeta(): AssetMeta = {
    AssetMeta.findById(asset_meta_id.id).get
  }
}

object AssetMetaValue extends Magic[AssetMetaValue](Some("asset_meta_value")) with Dao[AssetMetaValue] {

  def apply(asset_id: Long, asset_meta_id: Long, value: String) =
    new AssetMetaValue(Id(asset_id), Id(asset_meta_id), 0, value)

  def apply(asset_id: Long, asset_meta_id: Long, group_id: Int, value: String) =
    new AssetMetaValue(Id(asset_id), Id(asset_meta_id), group_id, value)

  def create(mvs: Seq[AssetMetaValue])(implicit con: Connection): Int = {
    mvs.foldLeft(0) { case(count, mv) =>
      AssetMetaValue.insert(mv) match {
        case _ => count + 1
      }
    }
  }

  def findOneByAssetId(spec: Set[AssetMeta.Enum], asset_id: Long): Seq[MetaWrapper] = {
    val query = """
      select * from asset_meta am
      join asset_meta_value amv on am.id = amv.asset_meta_id
      where amv.asset_id = {id}
    """
    val extra = spec.isEmpty match {
      case true => ""
      case false => " and amv.asset_meta_id in (%s)".format(spec.map { _.id }.mkString(","))
    }
    val finalQuery = query + extra
    Model.withConnection { implicit connection =>
      SQL(finalQuery).on('id -> asset_id).as(AssetMetaValue ~< AssetMeta ^^ flatten *).map {
        case (mv, m) => MetaWrapper(m, mv)
      }
    }
  }

  def findAllByAssetId(id: Long): Seq[MetaWrapper] = {
    val query = """
      select * from asset_meta_value amv
      join asset_meta am on am.id = amv.asset_meta_id
      where amv.asset_id={id}
    """
    Model.withConnection { implicit connection =>
      SQL(query).on('id -> id).as(AssetMeta ~< AssetMetaValue ^^ flatten *).map {
        case (meta, metaValue) => MetaWrapper(meta, metaValue)
      }
    }
  }

}


