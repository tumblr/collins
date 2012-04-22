package models

import conversions._
import util.{Feature, LldpRepresentation, LshwRepresentation}
import util.views.Formatter.dateFormat

import play.api.Logger
import play.api.libs.json._

import org.squeryl.Schema
import org.squeryl.PrimitiveTypeMode._
import org.squeryl.dsl.ast.{BinaryOperatorNodeLogicalBoolean, LogicalBoolean}

import java.sql.Timestamp
import java.util.Date

object AssetConfig {
  lazy val HiddenMeta: Set[String] = Feature("hideMeta").toSet
}

case class Asset(tag: String, status: Int, asset_type: Int,
    created: Timestamp, updated: Option[Timestamp], deleted: Option[Timestamp],
    id: Long = 0) extends ValidatedEntity[Long]
{
  override def validate() {
    require(Asset.isValidTag(tag), "Tag must be non-empty alpha numeric")
  }
  override def asJson: String = {
    Json.stringify(JsObject(forJsonObject))
  }

  def forJsonObject(): Seq[(String,JsValue)] = Seq(
    "ID" -> JsNumber(getId()),
    "TAG" -> JsString(tag),
    "STATUS" -> JsString(getStatus().name),
    "TYPE" -> JsString(getType().name),
    "CREATED" -> JsString(dateFormat(created)),
    "UPDATED" -> JsString(updated.map { dateFormat(_) }.getOrElse(""))
  )

  def getId(): Long = id
  def isNew(): Boolean = status == models.Status.Enum.New.id
  def isProvisioning(): Boolean = status == models.Status.Enum.Provisioning.id
  def isProvisioned(): Boolean = status == models.Status.Enum.Provisioned.id
  def isMaintenance(): Boolean = status == models.Status.Enum.Maintenance.id

  def getStatus(): Status = {
    Status.findById(status).get
  }
  def getType(): AssetType = {
    AssetType.findById(asset_type).get
  }
  def getMetaAttribute(name: String): Option[MetaWrapper] = {
    AssetMeta.findByName(name).flatMap { meta =>
      AssetMetaValue.findByAssetAndMeta(this, meta, 1) match {
        case Nil => None
        case head :: Nil => Some(head)
      }
    }
  }
  def getMetaAttribute(name: String, count: Int): Seq[MetaWrapper] = {
    AssetMeta.findByName(name).map { meta =>
      AssetMetaValue.findByAssetAndMeta(this, meta, count)
    }.getOrElse(Nil)
  }
  def getMetaAttribute(spec: AssetMeta.Enum): Option[MetaWrapper] = {
    getMetaAttribute(spec.toString)
  }

  def getAllAttributes: Asset.AllAttributes = {
    val (lshwRep, mvs) = LshwHelper.reconstruct(this)
    val (lldpRep, mvs2) = LldpHelper.reconstruct(this, mvs)
    val ipmi = IpmiInfo.findByAsset(this)
    val addresses = IpAddresses.findAllByAsset(this)
    val filtered: Seq[MetaWrapper] = mvs2.filter(f => !AssetConfig.HiddenMeta.contains(f.getName))
    Asset.AllAttributes(this, lshwRep, lldpRep, ipmi, addresses, filtered)
  }
}

object Asset extends Schema with AnormAdapter[Asset] {

  private[this] val TagR = """[A-Za-z0-9\-_]+""".r.pattern.matcher(_)
  private[this] val logger = Logger.logger
  override protected val createEventName = Some("asset_create")
  override protected val updateEventName = Some("asset_update")

  override val tableDef = table[Asset]("asset")
  on(tableDef)(a => declare(
    a.id is(autoIncremented,primaryKey),
    a.tag is(unique),
    a.status is(indexed),
    a.asset_type is(indexed),
    a.created is(indexed),
    a.updated is(indexed)
  ))
  override def cacheKeys(asset: Asset) = Seq(
    "Asset.findByTag(%s)".format(asset.tag.toLowerCase),
    "Asset.findById(%d)".format(asset.id)
  )

  def isValidTag(tag: String): Boolean = {
    tag != null && tag.nonEmpty && TagR(tag).matches
  }

  def apply(tag: String, status: Status.Enum, asset_type: AssetType.Enum) = {
    new Asset(tag, status.id, asset_type.id, new Date().asTimestamp, None, None)
  }
  def apply(tag: String, status: Status.Enum, asset_type: AssetType) = {
    new Asset(tag, status.id, asset_type.getId, new Date().asTimestamp, None, None)
  }

  override def delete(asset: Asset): Int = inTransaction {
    afterDeleteCallback(asset) {
      tableDef.deleteWhere(a => a.id === asset.id)
    }
  }

  def find(page: PageParams, params: util.AttributeResolver.ResultTuple, afinder: AssetFinder, operation: Option[String] = None): Page[Asset] = {
    if (params._1.nonEmpty) {
      IpmiInfo.findAssetsByIpmi(page, params._1, afinder)
    } else if (params._2.nonEmpty) {
      AssetMetaValue.findAssetsByMeta(page, params._2, afinder, operation)
    } else if (params._3.nonEmpty) {
      IpAddresses.findAssetsByAddress(page, params._3, afinder)
    } else {
      Asset.find(page, afinder)
    }
  }

  def find(page: PageParams, afinder: AssetFinder): Page[Asset] = inTransaction {
    val results = from(tableDef)(a =>
      where(afinder.asLogicalBoolean(a))
      select(a)
    ).page(page.offset, page.size).toList
    val totalCount = from(tableDef)(a =>
      where(afinder.asLogicalBoolean(a))
      compute(count)
    )
    Page(results, page.page, page.offset, totalCount)
  }

  def find(page: PageParams, finder: AssetFinder, assets: Set[Long]): Page[Asset] = inTransaction {
    assets.size match {
      case 0 => Page(Seq(), page.page, page.offset, 0)
      case n =>
        logger.debug("Starting Asset.find count")
        val totalCount: Long = from(tableDef)(asset =>
          where(asset.id in assets and finder.asLogicalBoolean(asset))
          compute(count)
        )
        logger.debug("Finished Asset.find count")
        val results = from(tableDef)(asset =>
          where(asset.id in assets and finder.asLogicalBoolean(asset))
          select(asset)
          orderBy(asset.id.withSort(page.sort))
        ).page(page.offset, page.size).toList
        logger.debug("Finished Asset.find find")
        Page(results, page.page, page.offset, totalCount)
    }
  }

  def findById(id: Long) = getOrElseUpdate("Asset.findById(%d)".format(id)) {
    tableDef.lookup(id)
  }
  def get(a: Asset) = findById(a.id).get

  def findByTag(tag: String): Option[Asset] = {
    getOrElseUpdate("Asset.findByTag(%s)".format(tag.toLowerCase)) {
      tableDef.where(a => a.tag.toLowerCase === tag.toLowerCase).headOption
    }
  }

  def findLikeTag(tag: String, params: PageParams): Page[Asset] = inTransaction {
    val results = from(tableDef)(a =>
      where(a.tag.withPossibleRegex(tag))
      select(a)
      orderBy(a.id.withSort(params.sort))
    ).page(params.offset, params.size).toList
    val totalCount = from(tableDef)(a =>
      where(a.tag.withPossibleRegex(tag))
      compute(count)
    )
    Page(results, params.page, params.offset, totalCount)
  }

  case class AllAttributes(asset: Asset, lshw: LshwRepresentation, lldp: LldpRepresentation, ipmi: Option[IpmiInfo], addresses: Seq[IpAddresses], mvs: Seq[MetaWrapper]) {
    def exposeCredentials(showCreds: Boolean = false) = {
      this.copy(ipmi = this.ipmi.map { _.withExposedCredentials(showCreds) })
          .copy(mvs = this.metaValuesWithExposedCredentials(showCreds))
    }

    protected def metaValuesWithExposedCredentials(showCreds: Boolean): Seq[MetaWrapper] = {
      if (showCreds) {
        mvs
      } else {
        mvs.filter(mv => !AssetMetaValueConfig.EncryptedMeta.contains(mv.getName))
      }
    }

    def toJsonObject(): JsObject = {
      val ipmiMap = ipmi.map { info =>
        info.forJsonObject
      }.getOrElse(Seq[(String,JsValue)]())
      val outSeq = Seq(
        "ASSET" -> JsObject(asset.forJsonObject),
        "HARDWARE" -> JsObject(lshw.forJsonObject),
        "LLDP" -> JsObject(lldp.forJsonObject),
        "IPMI" -> JsObject(ipmiMap),
        "ADDRESSES" -> JsArray(addresses.toList.map(j => JsObject(j.forJsonObject()))),
        "ATTRIBS" -> JsObject(mvs.groupBy { _.getGroupId }.map { case(groupId, mv) =>
          groupId.toString -> JsObject(mv.map { mvw => mvw.getName -> JsString(mvw.getValue) })
        }.toSeq)
      )
      JsObject(outSeq)
    }
  }
}

case class AssetFinder(
  tag: Option[String],
  status: Option[Status.Enum],
  assetType: Option[AssetType.Enum],
  createdAfter: Option[Date],
  createdBefore: Option[Date],
  updatedAfter: Option[Date],
  updatedBefore: Option[Date])
{
  def asLogicalBoolean(a: Asset): LogicalBoolean = {
    val tagBool = tag.map((a.tag === _))
    val statusBool = status.map((a.status === _.id))
    val typeBool = assetType.map((a.asset_type === _.id))
    val createdAfterTs = createdAfter.map((a.created gte _.asTimestamp))
    val createdBeforeTs = createdBefore.map((a.created lte _.asTimestamp))
    val updatedAfterTs = Some((a.updated gte updatedAfter.map(_.asTimestamp).?))
    val updatedBeforeTs = Some((a.updated lte updatedBefore.map(_.asTimestamp).?))
    val ops = Seq(tagBool, statusBool, typeBool, createdAfterTs, createdBeforeTs, updatedAfterTs,
      updatedBeforeTs).filter(_ != None).map(_.get)
    ops.reduceRight((a,b) => new BinaryOperatorNodeLogicalBoolean(a, b, "and"))
  }
}
