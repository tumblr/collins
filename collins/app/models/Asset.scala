package models

import Model.defaults._
import conversions._
import util.{Cache, Helpers, LldpRepresentation, LshwRepresentation}

import anorm._
import anorm.SqlParser._
import play.api.libs.json._

import org.squeryl.Schema

import java.sql.{Connection, Timestamp}
import java.util.Date

case class MockAsset(
  tag: String,
  status: Int,
  asset_type: Int,
  created: Timestamp,
  updated: Option[Timestamp],
  deleted: Option[Timestamp],
  id: Long) extends ValidatedEntity[Long]
{
  def getId(): Long = id
  override def validate() {
    require(Asset.isValidTag(tag), "Tag must be non-empty alpha numeric")
  }
}
object MockAsset extends Schema with AnormAdapter[MockAsset] {
  import org.squeryl.PrimitiveTypeMode._
  val tableDef = table[MockAsset]("asset")
  override def cacheKeys(a: MockAsset) = Seq(
  )
  override def delete(t: MockAsset): Int = {
    tableDef.deleteWhere(s => s.id === t.id)
  }
}

case class Asset(
    id: Pk[java.lang.Long],
    tag: String,
    status: Int,
    asset_type: Int,
    created: Timestamp, updated: Option[Timestamp], deleted: Option[Timestamp])
{
  require(Asset.isValidTag(tag), "Tag must be non-empty alpha numeric")

  def forJsonObject(): Seq[(String,JsValue)] = Seq(
    "ID" -> JsNumber(getId()),
    "TAG" -> JsString(tag),
    "STATUS" -> JsString(getStatus().name),
    "TYPE" -> JsString(getType().name),
    "CREATED" -> JsString(Helpers.dateFormat(created)),
    "UPDATED" -> JsString(updated.map { Helpers.dateFormat(_) }.getOrElse(""))
  )

  def getId(): Long = id.get
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

object Asset extends Magic[Asset](Some("asset")) {

  private[this] val TagR = """[A-Za-z0-9\-_]+""".r.pattern.matcher(_)
  def isValidTag(tag: String): Boolean = {
    tag != null && tag.nonEmpty && TagR(tag).matches
  }

  def apply(tag: String, status: Status.Enum, asset_type: AssetType.Enum) = {
    new Asset(NotAssigned, tag, status.id, asset_type.id, new Date().asTimestamp, None, None)
  }

  def apply(tag: String, status: Status.Enum, asset_type: AssetType) = {
    new Asset(NotAssigned, tag, status.id, asset_type.getId, new Date().asTimestamp, None, None)
  }

  override def create(asset: Asset)(implicit con: Connection) = {
    super.create(asset) match {
      case newasset =>
        Cache.invalidate("Asset.findByTag(%s)".format(asset.tag.toLowerCase))
        newasset
    }
  }

  override def update(asset: Asset)(implicit con: Connection) = {
    super.update(asset) match {
      case updated =>
        Cache.invalidate("Asset.findByTag(%s)".format(asset.tag.toLowerCase))
        Cache.invalidate("Asset.findById(%d)".format(asset.getId))
        updated
    }
  }

  def create(assets: Seq[Asset])(implicit con: Connection): Seq[Asset] = {
    assets.foldLeft(List[Asset]()) { case(list, asset) =>
      if (asset.id.isDefined) throw new IllegalArgumentException("id of asset must be NotAssigned")
      Asset.create(asset) +: list
    }.reverse
  }

  def findById(id: Long): Option[Asset] = Model.withConnection { implicit con =>
    Cache.getOrElseUpdate("Asset.findById(%d)".format(id)) {
      Asset.find("id={id}").on('id -> id).singleOption()
    }
  }
  def findByTag(tag: String): Option[Asset] = Model.withConnection { implicit con =>
    Cache.getOrElseUpdate("Asset.findByTag(%s)".format(tag.toLowerCase)) {
      Asset.find("tag={tag}").on('tag -> tag).first()
    }
  }
  def findLikeTag(tag: String, params: PageParams): Page[Asset] = Model.withConnection { implicit con =>
    val tags = tag + "%"
    val orderBy = params.sort.toUpperCase match {
      case "ASC" => "ORDER BY ID ASC"
      case _ => "ORDER BY ID DESC"
    }
    val assets = Asset.find("tag like {tag} %s limit {pageSize} offset {offset}".format(orderBy)).on(
      'tag -> tags,
      'pageSize -> params.size,
      'offset -> params.offset
    ).list()
    val count = Asset.count("tag like {tag}").on(
      'tag -> tags
    ).as(scalar[Long])
    Page(assets, params.page, params.offset, count)
  }

  def findByMeta(list: Seq[(AssetMeta.Enum,String)]): Seq[Asset] = {
    val query = "select distinct asset_id from asset_meta_value where "
    var count = 0
    val params = list.map { case(k,v) =>
      val id: String = k.toString + "_" + count
      count += 1
      val fragment = "asset_meta_value.asset_meta_id = %d and asset_meta_value.value like {%s}".format(k.id, id)
      (fragment, (Symbol(id), toParameterValue(v)))
    }
    val subquery = query + params.map { _._1 }.mkString(" and ")
    Model.withConnection { implicit connection =>
      Asset.find("select * from asset WHERE id in (%s)".format(subquery)).on(
        params.map(_._2):_*
      ).list()
    }
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
  // Without this, toParameterValue sees dates as java.util.Date instead of Timestamp and the wrong
  // ToStatement is used
  import DaoSupport._

  type Intable = {def id: Int}
  def asQueryFragment(): SimpleSql[Row] = {
    val _status = getEnumSimple("asset.status", status)
    val _atype = getEnumSimple("asset.asset_type", assetType)
    val _created = createDateSimple("asset.created", createdAfter, createdBefore)
    val _updated = createDateSimple("asset.updated", updatedAfter, updatedBefore)
    val _tag = tag.map { t =>
      val name = "%s_0".format("asset_tag")
      SqlQuery("%s={%s}".format("asset.tag", name)).on(name -> t)
    }
    flattenSql(Seq(_status, _atype, _created, _updated, _tag).collect { case Some(i) => i })
  }

  private def getEnumSimple(param: String, enum: Option[Intable]): Option[SimpleSql[Row]] = {
    enum.map { e =>
      val name = "%s_0".format(param.replace(".","_"));
      SqlQuery("%s={%s}".format(param, name)).on(name -> e.id)
    }
  }
  private def createDateSimple(param: String, after: Option[Date], before: Option[Date]): Option[SimpleSql[Row]] = {
    val afterName = "%s_after_0".format(param.replace(".","_"))
    val beforeName = "%s_before_0".format(param.replace(".","_"))
    val _after = after.map { date =>
      SqlQuery("%s >= {%s}".format(param, afterName)).on(afterName -> date.asTimestamp)
    }
    val _before = before.map { date =>
      SqlQuery("%s <= {%s}".format(param, beforeName)).on(beforeName -> date.asTimestamp)
    }
    val filtered: Seq[SimpleSql[Row]] = Seq(_after, _before).collect { case Some(i) => i }
    if (filtered.nonEmpty) {
      Some(flattenSql(filtered))
    } else {
      None
    }
  }
}
