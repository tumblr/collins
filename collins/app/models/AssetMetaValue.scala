package models

import conversions._
import util.{Cache, Helpers, InternalTattler}
import play.api.Logger
import java.sql.Timestamp
import java.util.Date

import org.squeryl.PrimitiveTypeMode._
import org.squeryl.{Query, Schema}
import org.squeryl.dsl.ast.{BinaryOperatorNodeLogicalBoolean, ExistsExpression, ExpressionNode, LogicalBoolean}

object AssetMetaValueConfig {
  lazy val ExcludedAttributes: Set[Long] = Helpers.getFeature("noLogPurges").map { v =>
    val noLogSet = v.split(",").map(_.trim.toUpperCase).toSet
    noLogSet.map(v => AssetMeta.findByName(v).map(_.getId).getOrElse(-1L))
  }.getOrElse(Set[Long]())
}

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
  type AssetMetaFinder = Seq[Tuple2[AssetMeta, String]]
  private[this] val logger = Logger.logger

  val tableDef = table[AssetMetaValue]("asset_meta_value")
  on(tableDef)(a => declare(
    a.asset_meta_id is(indexed),
    a.group_id is(indexed),
    a.group_id defaultsTo(0),
    columns(a.asset_id, a.asset_meta_id) are(indexed)
  ))

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

  override def cacheKeys(a: AssetMetaValue) = Seq(
    "AssetMetaValue.findByMeta(%d)".format(a.asset_meta_id),
    "AssetMetaValue.findByAsset(%d)".format(a.asset_id),
    fbam.format(a.asset_id, a.asset_meta_id)
  )

  def create(mvs: Seq[AssetMetaValue]): Int = inTransaction {
    try {
      tableDef.insert(mvs)
      mvs.size
    } catch {
      case e =>
        e.printStackTrace()
        0
    }
  }

  override def delete(a: AssetMetaValue): Int = inTransaction {
    tableDef.deleteWhere(p =>
      p.asset_id === a.asset_id and
      p.asset_meta_id === a.asset_meta_id
    )
  }

  def deleteByAsset(asset: Asset): Int = inTransaction {
    tableDef.deleteWhere(p => p.asset_id === asset.getId)
  }

  def deleteByAssetAndMetaId(asset: Asset, meta_id: Set[Long]): Int = inTransaction {
    tableDef.deleteWhere { p =>
      (p.asset_id === asset.getId) and
      (p.asset_meta_id in meta_id)
    }
  }

  def find(mv: AssetMetaValue, useValue: Boolean = true): Option[AssetMetaValue] = inTransaction {
    from(tableDef)(a =>
      where {
        a.asset_id === mv.asset_id and
        a.asset_meta_id === mv.asset_meta_id and
        a.value === mv.value.inhibitWhen(useValue == false)
      }
      select(a)
    ).headOption
  }

  def findAssetsByMeta(page: PageParams, toFind: AssetMetaFinder, afinder: AssetFinder,
                       op: Option[String]): Page[Asset] = {
    val whereClause = {amv: AssetMetaValue =>
      val e = excludes(amv, toFind, op)
      val i = includes(amv, toFind, op)
      mergeBooleans(e, i)
    }
    inTransaction {
      val assets = from(tableDef, Asset.tableDef)((amv, asset) =>
        where(whereClause(amv) and amv.asset_id === asset.id and afinder.asLogicalBoolean(asset))
        select(asset)
        orderBy(asset.id.withSort(page.sort))
      ).distinct.page(page.offset, page.size).toList
      val totalCount = from(tableDef, Asset.tableDef)((amv, asset) =>
        where(whereClause(amv) and amv.asset_id === asset.id and afinder.asLogicalBoolean(asset))
        compute(count)
      )
      Page(assets, page.page, page.offset, totalCount)
    }
  }

  def findByAsset(asset: Asset): Seq[MetaWrapper] = {
    getOrElseUpdate("AssetMetaValue.findByAsset(%d)".format(asset.id)) {
      from(tableDef)(a =>
        where(a.asset_id === asset.id)
        select(a)
      ).toList.map { amv =>
        MetaWrapper(amv.getMeta(), amv)
      }
    }
  }

  private[this] val fbam = "AssetMetaValue.findByAssetAndMeta(%d, %d)"
  def findByAssetAndMeta(asset: Asset, meta: AssetMeta, count: Int): Seq[MetaWrapper] = {
    getOrElseUpdate(fbam.format(asset.getId, meta.id)) {
      from(tableDef)(a =>
        where(a.asset_id === asset.getId and a.asset_meta_id === meta.id)
        select(a)
      ).page(0, count).toList.map(mv => MetaWrapper(meta, mv))
    }
  }

  def findByMeta(meta: AssetMeta): Seq[String] = {
    getOrElseUpdate("AssetMetaValue.findByMeta(%d)".format(meta.id)) {
      from(tableDef)(a =>
        where(a.asset_meta_id === meta.id)
        select(a.value)
      ).distinct.toList.sorted
    }
  }

  def purge(mvs: Seq[AssetMetaValue]) = {
    mvs.map(mv => (find(mv, false), mv)).foreach { case(oldValue, newValue) =>
      val deleteCount = deleteByAssetIdAndMetaId(newValue.asset_id, Set(newValue.asset_meta_id))
      if (deleteCount > 0 && shouldLogChange(oldValue, newValue)) {
        logChange(oldValue, newValue)
      } else {
        logger.debug("Got %d rows for AssetMetaValue.purge", deleteCount)
      }
    }
  }

  protected def deleteByAssetIdAndMetaId(asset_id: Long, meta_id: Set[Long]): Int = {
    inTransaction {
      tableDef.deleteWhere { p =>
        (p.asset_id === asset_id) and
        (p.asset_meta_id in meta_id)
      }
    }
  }

  protected def excludes(amv: AssetMetaValue, toFind: AssetMetaFinder, bool: Option[String]
    ): Option[LogicalBoolean] = {
    val clauses = toFind.filter(_._2.isEmpty)
    if (clauses.length == 0) {
      return None
    }
    // Don't need value for excludes match, just want assets that have a value
    val subqueries = clauses.map { case(am, v) =>
      from(tableDef)(a =>
        where(a.asset_id === amv.asset_id and a.asset_meta_id === am.id)
        select(&(1))
      )
    }
    Some(
      subqueries.map { s =>
        notExists(s): LogicalBoolean
      }.reduceRight((a,b) => new BinaryOperatorNodeLogicalBoolean(a, b, "and"))
    )
  }

  protected def includes(amv: AssetMetaValue, toFind: AssetMetaFinder, bool: Option[String]
    ): Option[LogicalBoolean] = {
    val isAnd = (bool.toBinaryOperator == "and")
    val clauses = toFind.filter(_._2.nonEmpty)
    if (clauses.length == 0) {
      return None
    }
    if (isAnd) {
      Some(
        clauses.map { case(am, v) =>
          exists(matchClause(amv, am, v)): LogicalBoolean
        }.reduceRight((a, b) => new BinaryOperatorNodeLogicalBoolean(a, b, "and"))
      )
    } else {
      Some(
        clauses.map { case(am, v) =>
          amv.asset_meta_id === am.id and amv.value.withPossibleRegex(v)
        }.reduceRight((a, b) => new BinaryOperatorNodeLogicalBoolean(a, b, "or"))
      )
    }
  }

  protected def logChange(oldValue: Option[AssetMetaValue], newValue: AssetMetaValue) {
    oldValue match {
      case None =>
      case Some(oValue) =>
        val msg = "Deleting old %s value '%s', setting to '%s".format(
                    AssetMeta.findById(newValue.asset_meta_id).map(_.name).getOrElse("Unknown"),
                    oValue.value, newValue.value)
        InternalTattler.notice(newValue.getAsset, None, msg)
    }
  }

  protected def shouldLogChange(oldValue: Option[AssetMetaValue], newValue: AssetMetaValue): Boolean = {
    oldValue.isDefined && AssetMetaValueConfig.ExcludedAttributes.contains(newValue.asset_meta_id)
  }

  private def matchClause(amv: AssetMetaValue, am: AssetMeta, v: String) = {
    from(tableDef)(a =>
      where(
        a.asset_id === amv.asset_id and a.asset_meta_id === am.id and a.value.withPossibleRegex(v)
      )
      select(&(1))
    )
  }

  private def mergeBooleans(o: Option[LogicalBoolean]*) = {
    o.filter(_.isDefined).map(_.get).reduceRight((a, b) =>
      new BinaryOperatorNodeLogicalBoolean(a, b, "and")
    )
  }
}
