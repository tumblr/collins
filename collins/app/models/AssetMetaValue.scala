package models

import conversions._
import util.{Cache, Helpers}
import play.api.Logger
import org.squeryl.PrimitiveTypeMode._
import org.squeryl.{Schema, Table}
import java.sql.{Connection, Timestamp}
import java.util.Date

case class AssetMetaValue(asset_id: Long, asset_meta_id: Long, group_id: Int, value: String) {
  def getAsset(): Asset = {
    Asset.findById(asset_id).get
  }
  def getAssetId(): Long = asset_id
  def getMeta(): AssetMeta = {
    AssetMeta.findById(asset_meta_id).get
  }
}

object AssetMetaValue extends Schema with BasicModel[AssetMetaValue] {

  private[this] val logger = Logger.logger
  val tableDef = table[AssetMetaValue]("asset_meta_value")
  on(tableDef)(a => declare(
    a.asset_meta_id is(indexed),
    a.group_id is(indexed),
    a.group_id defaultsTo(0),
    columns(a.asset_id, a.asset_meta_id) are(indexed)
  ))

  override def cacheKeys(a: AssetMetaValue) = Seq(
    "AssetMetaValue.findByMeta(%d)".format(a.asset_meta_id),
    "AssetMetaValue.findByAsset(%d)".format(a.asset_id),
    fbam.format(a.asset_id, a.asset_meta_id)
  )

  def apply(asset_id: Long, asset_meta_id: Long, value: String) =
    new AssetMetaValue(asset_id, asset_meta_id, 0, value)
  def apply(asset: Asset, asset_meta_id: Long, value: String) =
    new AssetMetaValue(asset.getId, asset_meta_id, 0, value)
  def apply(asset: Asset, asset_meta: AssetMeta.Enum, value: String) =
    new AssetMetaValue(asset.getId, asset_meta.id, 0, value)

  def apply(asset: Asset, asset_meta_id: Long, group_id: Int, value: String) =
    new AssetMetaValue(asset.getId, asset_meta_id, group_id, value)
  def apply(asset: Asset, asset_meta: AssetMeta.Enum, group_id: Int, value: String) =
    new AssetMetaValue(asset.getId, asset_meta.id, group_id, value)

  def exists(mv: AssetMetaValue): Boolean = withConnection {
    from(tableDef)(a =>
      where {
        a.asset_id === mv.asset_id and
        a.asset_meta_id === mv.asset_meta_id and
        a.value === mv.value
      }
      compute(count)
    ) > 0
  }

  def delete(a: AssetMetaValue): Int = withConnection {
    tableDef.deleteWhere(p =>
      p.asset_id === a.asset_id and
      p.asset_meta_id === a.asset_meta_id
    )
  }

  def deleteByAsset(asset: Asset): Int = withConnection {
    tableDef.deleteWhere(p => p.asset_id === asset.getId)
  }

  def deleteByAssetAndMetaId(asset: Asset, meta_id: Set[Long]): Int = withConnection {
    tableDef.deleteWhere { p =>
      (p.asset_id === asset.getId) and
      (p.asset_meta_id in meta_id)
    }
  }

  private[this] lazy val ExcludedAttributes: Set[Long] = Helpers.getFeature("noLogPurges").map { v =>
    val noLogSet = v.split(",").map(_.trim.toUpperCase).toSet
    noLogSet.map(v => AssetMeta.findByName(v).map(_.getId).getOrElse(-1L))
  }.getOrElse(Set[Long]())
  def purge(mvs: Seq[AssetMetaValue]) = {
    mvs.foreach { mv =>
      val exists = AssetMetaValue.exists(mv)
      val ami = mv.asset_meta_id
      deleteByAssetIdAndMetaId(mv.asset_id, Set(mv.asset_meta_id)) match {
        case yes if yes >= 1 && !exists && !ExcludedAttributes.contains(ami) =>
          AssetLog.notice(mv.getAsset,
                   "Deleted old meta value, setting %s to %s".format(
                     AssetMeta.findById(ami).map { _.name }.getOrElse(ami.toString),
                     mv.value),
                   LogFormat.PlainText, LogSource.Internal
           )
        case n =>
          logger.trace("Got %d rows for AssetMetaValue.delete", n)
      }
    }
  }

  def create(mvs: Seq[AssetMetaValue]): Int = withTransaction {
    try {
      mvs.foreach { mv => tableDef.insert(mv) }
      mvs.size
    } catch {
      case e =>
        e.printStackTrace()
        0
    }
  }

  def findByMeta(meta: AssetMeta): Seq[String] = {
    Cache.getOrElseUpdate("AssetMetaValue.findByMeta(%d)".format(meta.id)) {
      withConnection {
        from(tableDef)(a =>
          where(a.asset_meta_id === meta.id)
          select(a.value)
        ).distinct.toList.sorted
      }
    }
  }

  private[this] val fbam = "AssetMetaValue.findByAssetAndMeta(%d, %d)"
  def findByAssetAndMeta(asset: Asset, meta: AssetMeta, count: Int): Seq[MetaWrapper] = {
    Cache.getOrElseUpdate(fbam.format(asset.getId, meta.id)) {
    withConnection {
      from(tableDef)(a =>
        where(a.asset_id === asset.getId and a.asset_meta_id === meta.id)
        select(a)
      ).page(0, count).toList.map(mv => MetaWrapper(meta, mv))
    }}
  }

  def findByAsset(asset: Asset): Seq[MetaWrapper] = {
    Cache.getOrElseUpdate("AssetMetaValue.findByAsset(%d)".format(asset.id)) {
      withConnection {
        from(tableDef)(a =>
          where(a.asset_id === asset.id)
          select(a)
        ).toList.map { amv =>
          MetaWrapper(amv.getMeta(), amv)
        }
      }
    }
  }

  protected def deleteByAssetIdAndMetaId(asset_id: Long, meta_id: Set[Long]): Int = withConnection {
    tableDef.deleteWhere { p =>
      (p.asset_id === asset_id) and
      (p.asset_meta_id in meta_id)
    }
  }

}
