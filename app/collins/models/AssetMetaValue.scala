package collins.models

import org.squeryl.PrimitiveTypeMode._
import org.squeryl.Schema
import org.squeryl.dsl.ast.BinaryOperatorNodeLogicalBoolean
import org.squeryl.dsl.ast.LogicalBoolean

import play.api.Logger

import collins.models.shared.BasicModel
import collins.models.shared.Page
import collins.models.shared.PageParams

import collins.util.InternalTattler
import collins.util.CryptoCodec
import collins.util.config.Feature

import collins.models.asset.AssetView
import collins.models.conversions.ops2bo
import collins.models.conversions.reOrLike

case class AssetMetaValue(asset_id: Long, asset_meta_id: Long, group_id: Int, value: String) {

  def getAsset(): Asset = Asset.findById(asset_id).get
  def getAssetId(): Long = asset_id
  def getMeta(): AssetMeta = AssetMeta.findById(asset_meta_id).get

  require(asset_meta_id == 0 || getMeta().validateValue(value), "Invalid format for value" + value)

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

  def apply(asset: Asset, asset_meta_id: Long, value: String) =
    new AssetMetaValue(asset.getId, asset_meta_id, 0, value)
  def apply(asset: Asset, asset_meta: AssetMeta.Enum, value: String) =
    new AssetMetaValue(asset.getId, asset_meta.id, 0, value)
  def apply(asset: Asset, asset_meta_id: Long, group_id: Int, value: String) =
    new AssetMetaValue(asset.getId, asset_meta_id, group_id, value)
  def apply(asset: Asset, asset_meta: AssetMeta.Enum, group_id: Int, value: String) =
    new AssetMetaValue(asset.getId, asset_meta.id, group_id, value)
  def apply(asset: Asset, assetMeta: AssetMeta, value: String) =
    new AssetMetaValue(asset.getId, assetMeta.getId, 0, value)
  def apply(asset: Asset, assetMeta: AssetMeta, groupId: Int, value: String) =
    new AssetMetaValue(asset.getId, assetMeta.getId, groupId, value)

  override def callbacks = super.callbacks ++ Seq(
    beforeInsert(tableDef).map(v =>
      Option(shouldEncrypt(v))
        .filter(_ == true)
        .map(_ => getEncrypted(v))
        .getOrElse(v)
    )
  )
  override protected val createEventName = Some("asset_meta_value_create")
  override protected val deleteEventName = Some("asset_meta_value_delete")

  def shouldEncrypt(v: AssetMetaValue): Boolean = {
    try {
      Feature.encryptedTags.map(_.name).contains(v.getMeta().name)
    } catch {
      case e: Throwable =>
        logger.error("Caught exception trying to determine whether to encrypt", v)
        false
    }
  }
  def getEncrypted(v: AssetMetaValue): AssetMetaValue = {
    v.copy(value = CryptoCodec.withKeyFromFramework.Encode(v.value))
  }

  def create(mvs: Seq[AssetMetaValue]): Int = inTransaction {
    try {
      tableDef.insert(mvs)
      mvs.size
    } catch {
      case e: Throwable =>
        logger.error("Caught exception trying to insert rows: %s".format(e.getMessage), e)
        0
    }
  }

  override def delete(a: AssetMetaValue): Int = inTransaction {
    afterDeleteCallback(a) {
      tableDef.deleteWhere(p =>
        p.asset_id === a.asset_id and
        p.asset_meta_id === a.asset_meta_id
      )
    }
  }

  def deleteByAsset(asset: Asset): Int = inTransaction {
    val meta = AssetMetaValue(asset, 0, "")
    afterDeleteCallback(meta) {
      tableDef.deleteWhere(p => p.asset_id === asset.getId)
    }
  }

  def deleteByAssetAndMetaId(asset: Asset, meta_id: Set[Long]): Int = inTransaction {
    deleteByAssetIdAndMetaId(asset.id, meta_id, None)
  }

  def find(mv: AssetMetaValue, useValue: Boolean, groupId: Option[Int]): Option[AssetMetaValue] = inTransaction { log {
    from(tableDef)(a =>
      where {
        a.asset_id === mv.asset_id and
        a.asset_meta_id === mv.asset_meta_id and
        a.group_id === groupId.? and
        a.value === mv.value.inhibitWhen(useValue == false)
      }
      select(a)
    ).headOption
  }}

  def findAssetsByMeta(page: PageParams, toFind: AssetMetaFinder, afinder: AssetFinder,
                       op: Option[String]): Page[AssetView] = {
    val whereClause = {asset: Asset =>
      val e = excludes(asset, toFind, op)
      val i = includes(asset, toFind, op)
      mergeBooleans(e, i)
    }
    inTransaction { log {
      logger.debug("Starting asset collection")
      val assets = from(Asset.tableDef)(asset =>
        where(whereClause(asset) and afinder.asLogicalBoolean(asset))
        select(asset)
      ).distinct.page(page.offset, page.size).toList
      val totalCount: Long = from(Asset.tableDef)(asset =>
        where(whereClause(asset) and afinder.asLogicalBoolean(asset))
        compute(count)
      )
      logger.debug("Finished asset collection")
      Page(assets, page.page, page.offset, totalCount)
    }}
  }

  def findByAsset(asset: Asset, checkCache: Boolean = true): Seq[MetaWrapper] = inTransaction {
      from(tableDef)(a =>
        where(a.asset_id === asset.id)
        select(a)
      ).toList.map { amv =>
        MetaWrapper(amv.getMeta(), amv)
      }
  }

  def findByAssetAndMeta(asset: Asset, meta: AssetMeta, count: Int): Seq[MetaWrapper] = inTransaction {
    from(tableDef)(a =>
      where(a.asset_id === asset.getId and a.asset_meta_id === meta.id)
      select(a)
    ).page(0, count).toList.map(mv => MetaWrapper(meta, mv))
  }

  def findByMeta(meta: AssetMeta): Seq[String] = inTransaction {
    from(tableDef)(a =>
      where(a.asset_meta_id === meta.id)
      select(a.value)
    ).distinct.toList.sorted
  }

  def purge(mvs: Seq[AssetMetaValue], groupId: Option[Int]) = {
    mvs.map(mv => (find(mv, false, groupId), mv)).foreach { case(oldValue, newValue) =>
      val deleteCount = deleteByAssetIdAndMetaId(newValue.asset_id, Set(newValue.asset_meta_id), groupId)
      if (deleteCount > 0 && shouldLogChange(oldValue, newValue)) {
        logChange(oldValue, newValue)
      } else {
        logger.debug("Got %d rows for AssetMetaValue.purge".format(deleteCount))
      }
    }
  }

  protected def deleteByAssetIdAndMetaId(asset_id: Long, meta_id: Set[Long], groupId: Option[Int]): Int = {
    inTransaction {
      val results = tableDef.deleteWhere { p =>
        (p.asset_id === asset_id) and
        (p.group_id === groupId.?) and
        (p.asset_meta_id in meta_id)
      }
      meta_id.foreach { id =>
        val meta = new AssetMetaValue(asset_id, id, 0, "")
        afterDeleteCallback(meta) {
          // no op
        }
      }
      results
    }
  }

  // Generates parts of findAssetsByMeta query for exclusion in results based on parameter
  // values that are empty.
  protected def excludes(asset: Asset, toFind: AssetMetaFinder, bool: Option[String]
    ): Option[LogicalBoolean] = {
    val clauses = toFind.filter(_._2.isEmpty)
    if (clauses.length == 0) {
      return None
    }
    // Don't need value for excludes match, just want assets that have a value
    val subqueries = clauses.map { case(am, v) =>
      from(tableDef)(a =>
        where(a.asset_id === asset.id and a.asset_meta_id === am.id)
        select(&(1))
      )
    }
    Some(
      subqueries.map { s =>
        notExists(s): LogicalBoolean
      }.reduceRight((a,b) => new BinaryOperatorNodeLogicalBoolean(a, b, "and"))
    )
  }

  // Generate parts of the findAssetsByMeta query for inclusion in results
  protected def includes(asset: Asset, toFind: AssetMetaFinder, bool: Option[String]
    ): Option[LogicalBoolean] = {
    val operator = bool.toBinaryOperator
    val clauses = toFind.filter(_._2.nonEmpty)
    if (clauses.length == 0) {
      return None
    }
    val clauseExpressions = clauses.map { case(am, v) =>
      exists(matchClause(asset, am, v)): LogicalBoolean
    }.reduceRight((a, b) => new BinaryOperatorNodeLogicalBoolean(a, b, operator))
    Some(clauseExpressions)
  }

  protected def logChange(oldValue: Option[AssetMetaValue], newValue: AssetMetaValue) {
    oldValue match {
      case None =>
      case Some(oValue) =>
        val metaName = newValue.getMeta().name
        val newValueS = if (Feature.encryptedTags.map(_.name).contains(metaName)) {
          val msg = "Value of '%s' was changed".format(metaName)
          InternalTattler.notice(newValue.getAsset, None, msg)
        } else {
          val msg = "Deleting old %s value '%s', setting to '%s'".format(
                    AssetMeta.findById(newValue.asset_meta_id).map(_.name).getOrElse("Unknown"),
                    oValue.value, newValue.value)
          InternalTattler.notice(newValue.getAsset, None, msg)
        }
    }
  }

  protected def shouldLogChange(oldValue: Option[AssetMetaValue], newValue: AssetMetaValue): Boolean = {
    val newAsset = Asset.findById(newValue.asset_id)
    val excludeAsset = newAsset.isDefined && Feature.noLogAssets.map(_.toLowerCase).contains(newAsset.get.tag.toLowerCase)
    oldValue.isDefined &&
    !Feature.noLogPurges.map(AssetMeta.findByName(_).map(_.id).getOrElse(0L)).contains(newValue.asset_meta_id) &&
    !excludeAsset &&
    oldValue.get.value != newValue.value
  }

  private def matchClause(asset: Asset, am: AssetMeta, v: String) = {
    from(tableDef)(a =>
      where(
        a.asset_id === asset.id and a.asset_meta_id === am.id and a.value.withPossibleRegex(v)
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
