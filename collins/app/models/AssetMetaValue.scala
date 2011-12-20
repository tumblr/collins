package models

import Model.defaults._

import anorm._
import anorm.SqlParser._
import java.sql.Connection

case class AssetMetaValue(asset_id: Id[java.lang.Long], asset_meta_id: Id[java.lang.Integer], group_id: Int, value: String) {
  def getAsset(): Asset = {
    Asset.findById(asset_id.id).get
  }
  def getMeta(): AssetMeta = {
    AssetMeta.findById(asset_meta_id.id).get
  }
}

object AssetMetaValue extends Magic[AssetMetaValue](Some("asset_meta_value")) {

  def apply(asset_id: Long, asset_meta_id: Int, value: String) =
    new AssetMetaValue(Id(asset_id), Id(asset_meta_id), 0, value)
  def apply(asset: Asset, asset_meta_id: Int, value: String) =
    new AssetMetaValue(Id(asset.getId), Id(asset_meta_id), 0, value)

  def apply(asset_id: Long, asset_meta_id: Int, group_id: Int, value: String) =
    new AssetMetaValue(Id(asset_id), Id(asset_meta_id), group_id, value)
  def apply(asset: Asset, asset_meta_id: Int, group_id: Int, value: String) =
    new AssetMetaValue(Id(asset.getId), Id(asset_meta_id), group_id, value)

  override def create(mv: AssetMetaValue)(implicit con: Connection): AssetMetaValue = {
    AssetMetaValue.insert(mv)
    mv
  }

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
      select * from asset_meta am
      join asset_meta_value amv on am.id = amv.asset_meta_id
      where amv.asset_id={id}
    """
    Model.withConnection { implicit connection =>
      SQL(query).on('id -> id).as(AssetMeta ~< AssetMetaValue ^^ flatten *).map {
        case (meta, metaValue) => MetaWrapper(meta, metaValue)
      }
    }
  }

}


