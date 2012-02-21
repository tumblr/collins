package models

import Model.defaults._
import conversions._
import util.Cache

import anorm._
import anorm.SqlParser._
import play.api.Logger
import java.sql.{Connection, Timestamp}
import java.util.Date

case class AssetMetaValue(asset_id: Id[java.lang.Long], asset_meta_id: Id[java.lang.Long], group_id: Int, value: String) {
  def getAsset(): Asset = {
    Asset.findById(asset_id.id).get
  }
  def getMeta(): AssetMeta = {
    AssetMeta.findById(asset_meta_id.id).get
  }
}

object AssetMetaValue extends Magic[AssetMetaValue](Some("asset_meta_value")) {
  private[this] val logger = Logger.logger

  def apply(asset_id: Long, asset_meta_id: Long, value: String) =
    new AssetMetaValue(Id(asset_id), Id(asset_meta_id), 0, value)
  def apply(asset: Asset, asset_meta_id: Long, value: String) =
    new AssetMetaValue(Id(asset.getId), Id(asset_meta_id), 0, value)
  def apply(asset: Asset, asset_meta: AssetMeta.Enum, value: String) =
    new AssetMetaValue(Id(asset.getId), Id(asset_meta.id), 0, value)

  def apply(asset_id: Long, asset_meta_id: Long, group_id: Int, value: String) =
    new AssetMetaValue(Id(asset_id), Id(asset_meta_id), group_id, value)
  def apply(asset: Asset, asset_meta_id: Long, group_id: Int, value: String) =
    new AssetMetaValue(Id(asset.getId), Id(asset_meta_id), group_id, value)
  def apply(asset: Asset, asset_meta: AssetMeta.Enum, group_id: Int, value: String) =
    new AssetMetaValue(Id(asset.getId), Id(asset_meta.id), group_id, value)

  def exists(mv: AssetMetaValue, con: Connection): Boolean = {
    implicit val c: Connection = con
    AssetMetaValue.count("asset_id={aid} AND asset_meta_id={ami} AND value={value}").on(
      'aid -> mv.asset_id.get,
      'ami -> mv.asset_meta_id.get,
      'value -> mv.value
    ).as(scalar[Long]) > 0
  }

  def purge(mvs: Seq[AssetMetaValue])(implicit con: Connection) = {
    mvs.foreach { mv =>
      val exists = AssetMetaValue.exists(mv, con)
      AssetMetaValue.delete("asset_id={aid} AND asset_meta_id={ami}").on(
        'aid -> mv.asset_id.get,
        'ami -> mv.asset_meta_id.get
      ).executeUpdate() match {
        case yes if yes == 1 && !exists =>
          val ami = mv.asset_meta_id.get
          AssetLog(NotAssigned, mv.asset_id, new Date().asTimestamp, AssetLog.Formats.PlainText.id.toByte,
                   AssetLog.Sources.Internal.id.toByte,
                   AssetLog.MessageTypes.Notice.id.toByte,
                   "Deleted old meta value, setting %s to %s".format(
                     AssetMeta.findById(ami).map { _.name }.getOrElse(ami.toString),
                     mv.value)
                   ).create()
        case n =>
          logger.trace("Got %d rows for AssetMetaValue.delete", n)
      }
    }
  }

  override def create(mv: AssetMetaValue)(implicit con: Connection): AssetMetaValue = {
    AssetMetaValue.insert(mv)
    Cache.invalidate("AssetMetaValue.findMetaValues(%d)".format(mv.asset_meta_id.get))
    mv
  }

  def create(mvs: Seq[AssetMetaValue])(implicit con: Connection): Int = {
    mvs.foldLeft(0) { case(count, mv) =>
      AssetMetaValue.create(mv) match {
        case _ => count + 1
      }
    }
  }

  def findMetaValues(meta_id: Long): Seq[String] = {
    val query = """
      select distinct value from asset_meta_value amv
      where amv.asset_meta_id = {id}
    """
    Model.withConnection { implicit con =>
      Cache.getOrElseUpdate("AssetMetaValue.findMetaValues(%d)".format(meta_id)) {
        SQL(query).on('id -> meta_id).as(str("value") *).sorted
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


