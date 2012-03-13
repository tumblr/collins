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
      mvs.foreach { mv => tableDef.insert(mv) }
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
      val expressions: Seq[LogicalBoolean] = Seq(
        includes(amv, toFind, op).map(exists(_)),
        excludes(amv, toFind, op).map(notExists(_))
      ).filter(_ != None).map(_.get)
      val safeExpressions = expressions match {
        case Nil => Seq((1 === 1))
        case list => list
      }
      safeExpressions.reduceRight{
        (a,b) => new BinaryOperatorNodeLogicalBoolean(a, b, "and")
      }
    }

    inTransaction {
      val assetIds: Set[Long] = from(AssetMetaValue.tableDef)(amv =>
        where(whereClause(amv))
        select(amv.asset_id)
        orderBy(amv.asset_id.withSort(page.sort))
      ).distinct.page(page.offset, page.size).toSet
      val totalCount: Long = from(AssetMetaValue.tableDef)(amv =>
        where(whereClause(amv))
        compute(countDistinct(amv.asset_id))
      )
      val assets = Asset.find(assetIds)
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
}
