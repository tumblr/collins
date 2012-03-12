package models

import conversions._
import util.{Cache, Helpers, LldpRepresentation, LshwRepresentation}

import play.api.libs.json._

import org.squeryl.Schema
import org.squeryl.PrimitiveTypeMode._
import org.squeryl.dsl.ast.{BinaryOperatorNodeLogicalBoolean, LogicalBoolean}

import java.sql.Timestamp
import java.util.Date

case class Asset(tag: String, status: Int, asset_type: Int,
    created: Timestamp, updated: Option[Timestamp], deleted: Option[Timestamp],
    id: Long = 0) extends ValidatedEntity[Long]
{
  override def validate() {
    require(Asset.isValidTag(tag), "Tag must be non-empty alpha numeric")
  }

  def forJsonObject(): Seq[(String,JsValue)] = Seq(
    "ID" -> JsNumber(getId()),
    "TAG" -> JsString(tag),
    "STATUS" -> JsString(getStatus().name),
    "TYPE" -> JsString(getType().name),
    "CREATED" -> JsString(Helpers.dateFormat(created)),
    "UPDATED" -> JsString(updated.map { Helpers.dateFormat(_) }.getOrElse(""))
  )

  def getId(): Long = id
  def isNew(): Boolean = {
    status == models.Status.Enum.New.id
  }
  def getStatus(): Status = {
    Status.findById(status).get
  }
  def getType(): AssetType = {
    AssetType.findById(asset_type).get
  }
  def getMetaAttribute(name: String): Option[MetaWrapper] = {
    MetaWrapper.findMeta(this, name)
  }
  def getMetaAttribute(name: String, count: Int): Seq[MetaWrapper] = {
    MetaWrapper.findMeta(this, name, count)
  }
  def getMetaAttribute(spec: AssetMeta.Enum): Option[MetaWrapper] = {
    MetaWrapper.findMeta(this, spec.toString, 1).toList match {
      case Nil => None
      case one :: Nil =>
        Some(one)
      case other =>
        throw new IndexOutOfBoundsException("Expected one value, if any")
    }
  }

  private[this] lazy val HiddenMeta: Set[String] = Helpers.getFeature("hideMeta")
      .map(_.split(",").map(_.trim.toUpperCase).toSet)
      .getOrElse(Set[String]())
  def getAllAttributes: Asset.AllAttributes = {
    val (lshwRep, mvs) = LshwHelper.reconstruct(this)
    val (lldpRep, mvs2) = LldpHelper.reconstruct(this, mvs)
    val ipmi = IpmiInfo.findByAsset(this)
    val filtered: Seq[MetaWrapper] = mvs2.filter(f => !HiddenMeta.contains(f.getName))
    Asset.AllAttributes(this, lshwRep, lldpRep, ipmi, filtered)
  }
}

object Asset extends Schema with AnormAdapter[Asset] {

  private[this] val TagR = """[A-Za-z0-9\-_]+""".r.pattern.matcher(_)

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

  override def delete(asset: Asset): Int = withConnection {
    tableDef.deleteWhere(a => a.id === asset.id)
  }

  def find(page: PageParams, afinder: AssetFinder): Page[Asset] = withConnection {
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

  def find(assets: Set[Long]): Seq[Asset] = withConnection {
    assets.size match {
      case 0 => Seq()
      case n => tableDef.where(asset => asset.id in assets).toList
    }
  }

  def findById(id: Long) = Cache.getOrElseUpdate("Asset.findById(%d)".format(id)) {
    withConnection {
      tableDef.lookup(id)
    }
  }

  def findByTag(tag: String): Option[Asset] = {
    Cache.getOrElseUpdate("Asset.findByTag(%s)".format(tag.toLowerCase)) {
      withConnection {
        tableDef.where(a => a.tag.toLowerCase === tag.toLowerCase).headOption
      }
    }
  }

  def findLikeTag(tag: String, params: PageParams): Page[Asset] = withConnection {
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

  case class AllAttributes(asset: Asset, lshw: LshwRepresentation, lldp: LldpRepresentation, ipmi: Option[IpmiInfo], mvs: Seq[MetaWrapper]) {
    def exposeCredentials(showCreds: Boolean = false) = {
      this.copy(ipmi = this.ipmi.map { _.withExposedCredentials(showCreds) })
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
    val statusId: Option[Int] = status.map(_.id)
    val typeId: Option[Int] = assetType.map(_.id)
    val createdAfterTs: Option[Timestamp] = createdAfter.map(_.asTimestamp)
    val createdBeforeTs: Option[Timestamp] = createdBefore.map(_.asTimestamp)
    val updatedAfterTs: Option[Timestamp] = updatedAfter.map(_.asTimestamp)
    val updatedBeforeTs: Option[Timestamp] = updatedBefore.map(_.asTimestamp)
    val ops = List[LogicalBoolean](
      (a.tag === tag.?) and
      (a.status === statusId.?) and
      (a.created gte createdAfterTs.?) and
      (a.created lte createdBeforeTs.?) and
      (a.updated gte updatedAfterTs.?) and
      (a.updated lte updatedBeforeTs.?)
    )
    ops.reduceRight((a,b) => new BinaryOperatorNodeLogicalBoolean(a, b, "and"))
  }
}
