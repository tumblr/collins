package models

import conversions._
import util.{AttributeResolver, Config, Feature, LldpRepresentation, LshwRepresentation, MessageHelper, Stats}
import util.power.PowerUnits
import util.views.Formatter.dateFormat
import util.plugins.solr._

import play.api.Logger
import play.api.libs.json._
import play.api.libs.ws.WS
import play.api._
import play.api.mvc._
import play.api.cache.Cache
import play.api.Play.current

import org.squeryl.Schema
import org.squeryl.PrimitiveTypeMode._

import java.net.URL
import java.sql.Timestamp
import java.text.SimpleDateFormat
import java.util.Date

import akka.dispatch.Await
import akka.util.duration._

import SortDirection._
import SortType._

object AssetConfig {
  lazy val HiddenMeta: Set[String] = Feature("hideMeta").toSet
}

/**
 * An AssetView can be either a regular Asset or a RemoteAsset from another
 * collins instance.  This interface should only expose methods needed by the
 * list view, since from there the client is directed to whichever instnace
 * actually owns the asset.
 */
trait AssetView {

  def tag: String
  //def status: Int
  //def asset_type: Int
  def created: Timestamp
  def updated: Option[Timestamp]

  def toJsonObject(): JsObject
  def forJsonObject: Seq[(String, JsValue)]

  def getHostnameMetaValue(): Option[String]
  def getPrimaryRoleMetaValue(): Option[String]
  def getStatusName(): String

  def remoteHost: Option[String] //none if local
  
  protected def jsonDateToTimestamp(j: JsValue): Option[Timestamp] = {
    val formatter = new SimpleDateFormat(util.views.Formatter.ISO_8601_FORMAT)
    j.asOpt[String].filter{_ != ""}.map{s => new Timestamp(formatter.parse(s).getTime)}
  }

}

case class RemoteCollinsHost(url: URL) {
  val credentials = url.getUserInfo().split(":", 2)
  require(credentials.size == 2, "Must have username and password")
  val username = credentials(0)
  val password = credentials(1)
  val path = Option(url.getPath).filter(_.nonEmpty).getOrElse("/").replaceAll("/+$", "")
  def host: String = url.getPort match {
    case none if none < 0 =>
      "%s://%s%s".format(url.getProtocol, url.getHost, path)
    case port =>
      "%s://%s:%d%s".format(url.getProtocol, url.getHost, port, path)
  }
  def hostWithCredentials: String = url.toString.replaceAll("/+$", "")
}
object RemoteCollinsHost {
  def apply(url: String) = new RemoteCollinsHost(new URL(url))
}

trait RemoteAsset extends AssetView {
  val json: JsObject
  val hostTag: String //the asset representing the data center this asset belongs to
  val remoteUrl: String

  def remoteHost = Some(remoteUrl)

  def toJsonObject = JsObject(forJsonObject)
  def forJsonObject = json.fields :+ ("LOCATION" -> JsString(hostTag)) //remoteUrl))
}

/**
 * A remote asset that extracts from json returned by collins when details is false
 */
case class BasicRemoteAsset(hostTag: String, remoteUrl: String, json: JsObject) extends RemoteAsset {

  def tag = (json \ "TAG").as[String]
  def created = jsonDateToTimestamp(json \ "CREATED").getOrElse(new Timestamp(0))
  def updated = jsonDateToTimestamp(json \ "UPDATED")

  private[this] def warnAboutData(){
    Logger.logger.warn("Attempting to retrieve details data on basic remote asset")

  }

  def getHostnameMetaValue() = {
    warnAboutData()
    None
  }
  def getPrimaryRoleMetaValue() = {
    warnAboutData()
    None
  }

  def getStatusName() = (json \ "STATUS" ).asOpt[String].getOrElse("Unknown")
}
  

/**
 * An asset controlled by another collins instance, used during multi-collins
 * searching
 */
case class DetailedRemoteAsset(hostTag: String, remoteUrl: String, json: JsObject) extends RemoteAsset {
  def tag = (json \ "ASSET" \ "TAG").as[String]
  def created = jsonDateToTimestamp(json \ "ASSET" \ "CREATED").getOrElse(new Timestamp(0))
  def updated = jsonDateToTimestamp(json \ "ASSET" \ "UPDATED")
  def getHostnameMetaValue() = (json \ "ATTRIBS" \ "0" \ "HOSTNAME").asOpt[String]
  def getPrimaryRoleMetaValue() = (json \ "ATTRIBS" \ "0" \ "PRIMARY_ROLE").asOpt[String]
  def getStatusName() = (json \ "ASSET" \ "STATUS" ).asOpt[String].getOrElse("Unknown")
}


case class Asset(tag: String, status: Int, asset_type: Int,
    created: Timestamp, updated: Option[Timestamp], deleted: Option[Timestamp],
    id: Long = 0) extends ValidatedEntity[Long] with AssetView
{
  override def validate() {
    require(Asset.isValidTag(tag), "Tag must be non-empty alpha numeric")
  }
  override def asJson: String = {
    Json.stringify(JsObject(forJsonObject))
  }

  def getHostnameMetaValue = getMetaAttribute("HOSTNAME").map{_.getValue}
  def getPrimaryRoleMetaValue = getMetaAttribute("PRIMARY_ROLE").map{_.getValue}

  def toJsonObject() = JsObject(forJsonObject)
  def forJsonObject(): Seq[(String,JsValue)] = Seq(
    "ID" -> JsNumber(getId()),
    "TAG" -> JsString(tag),
    "STATUS" -> JsString(getStatus().name),
    "TYPE" -> JsString(getType().name),
    "CREATED" -> JsString(dateFormat(created)),
    "UPDATED" -> JsString(updated.map { dateFormat(_) }.getOrElse(""))
  )

  def getId(): Long = id
  def isServerNode(): Boolean = asset_type == AssetType.Enum.ServerNode.id
  def isConfiguration(): Boolean = asset_type == AssetType.Enum.Config.id
  def isIncomplete(): Boolean = status == models.Status.Enum.Incomplete.id
  def isNew(): Boolean = status == models.Status.Enum.New.id
  def isProvisioning(): Boolean = status == models.Status.Enum.Provisioning.id
  def isProvisioned(): Boolean = status == models.Status.Enum.Provisioned.id
  def isMaintenance(): Boolean = status == models.Status.Enum.Maintenance.id

  def getStatus(): Status = {
    Status.findById(status).get
  }

  def getStatusName(): String = getStatus().name

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
    if (isConfiguration) {
      Asset.AllAttributes(this,
        LshwRepresentation.empty,
        LldpRepresentation.empty,
        None, Seq(), PowerUnits(),
        AssetMetaValue.findByAsset(this)
      )
    } else {
      val (lshwRep, mvs) = LshwHelper.reconstruct(this)
      val (lldpRep, mvs2) = LldpHelper.reconstruct(this, mvs)
      val ipmi = IpmiInfo.findByAsset(this)
      val addresses = IpAddresses.findAllByAsset(this)
      val (powerRep, mvs3) = PowerHelper.reconstruct(this, mvs2)
      val filtered: Seq[MetaWrapper] = mvs3.filter(f => !AssetConfig.HiddenMeta.contains(f.getName))
      Asset.AllAttributes(this, lshwRep, lldpRep, ipmi, addresses, powerRep, filtered)
    }
  }

  /**
   * Returns the nodeclass of this asset.  Nodeclass is found by getting all
   * nodeclass assets and finding the first one whose meta values match the values
   * of this asset
   */
  def nodeClass: Option[Asset] = {
    import util.AttributeResolver._
    val nodeclassType = Config.getString("nodeclass.assetType","CONFIGURATION").trim.toString
    val enum = try AssetType.Enum.withName(nodeclassType) catch {
      case e: java.util.NoSuchElementException => {
        Logger.logger.error("Invalid nodeclass asset type \"%s\", defaulting to configuration type".format(nodeclassType))
        AssetType.Enum.Config
      }
    }
    val instanceFinder = AssetFinder
      .empty
      .copy ( 
        assetType = Some(enum)
      )
    val nodeclassParams: ResolvedAttributes = EmptyResolvedAttributes
      .withMeta(Config.getString("nodeclass.identifyingMetaTag", "IS_NODECLASS"), "true")
    val nodeclasses = AssetMetaValue
      .findAssetsByMeta(PageParams(0,50,"ASC"), nodeclassParams.assetMeta, instanceFinder, Some("and"))
      .items
      .collect{case a: Asset => a}
    val myMetaSeq = this.metaSeq
    //Note - we cannot use set operations because an asset may contain multiple values of the same meta
    nodeclasses.map{n => 
      val metaseq = n.filteredMetaSeq
      if (metaseq.foldLeft(true){(ok, metaval) => ok && (myMetaSeq contains metaval)}) {
        Logger.logger.debug("%s,%d".format(n.toString, metaseq.size))
        Some(n -> metaseq.size)
      } else {
        Logger.logger.debug("%s,NONE".format(n.toString))
        None
      }
    }.flatten.sortWith{(a,b) => a._2 > b._2}.headOption.map{_._1}
  }

  private[models] def metaSeq: Seq[(AssetMeta,String)] = AssetMetaValue
    .findByAsset(this)
    .map{wrapper => (wrapper._meta -> wrapper._value.value)}

  /**
   * Filters out nodeclass exluded tags from the meta set
   */
  private[models] def filteredMetaSeq = {
    val excludeMetaTags = Config.getString("nodeclass.excludeMetaTags","").split(",")
    metaSeq.filter{case(meta, value) => !(excludeMetaTags contains meta.name)}
  }

  def remoteHost = None
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

  object Messages extends MessageHelper("asset") {
    def intakeError(t: String, a: Asset) = "intake.error.%s".format(t.toLowerCase) match {
      case msg if msg == "intake.error.new" =>
        messageWithDefault(msg, "Invalid asset status", a.getStatus.name)
      case msg if msg == "intake.error.type" =>
        messageWithDefault(msg, "Invalid asset type", a.getType.name)
      case msg if msg == "intake.error.disabled" =>
        messageWithDefault(msg, "Intake is disabled")
      case msg if msg == "intake.error.permissions" =>
        messageWithDefault(msg, "User does not have permission for intake")
      case other =>
        messageWithDefault(other, other)
    }
    def invalidId(id: Long) =
      messageWithDefault("id.notfound", "Asset not found", id.toString)
    def invalidTag(t: String) =
      messageWithDefault("tag.invalid", "Specified asset tag is invalid", t)
    def notFound(t: String) = message("missing", t)
    def noMatch() = message("nomatch")
  }

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

  def find(page: PageParams, params: util.AttributeResolver.ResultTuple, afinder: AssetFinder, operation: Option[String] = None, sortField: String = "TAG"): Page[AssetView] =
  Stats.time("Asset.find") {
    AssetSearchParameters(params, afinder, operation)
      .toSolrExpression
      .typeCheck
      .right
      .flatMap{exp => CollinsSearchQuery(exp, page, sortField).getPage()}
      .fold(
        err => throw new Exception(err),
        page => page
      )
  }

  def findById(id: Long) = getOrElseUpdate("Asset.findById(%d)".format(id)) {
    tableDef.lookup(id)
  }
  def get(a: Asset) = findById(a.id).get

  /**
   * checkCache is needed for solr re-indexing, avoids indexing out-of-date data
   */
  def findByTag(tag: String, checkCache: Boolean = true): Option[Asset] = {
    lazy val op = tableDef.where(a => a.tag.toLowerCase === tag.toLowerCase).headOption
    if (checkCache) {
      getOrElseUpdate("Asset.findByTag(%s)".format(tag.toLowerCase))(op)
    } else inTransaction {
      op
    }
  }

  def findLikeTag(tag: String, params: PageParams): Page[AssetView] = inTransaction { log {
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
  }}

  /**
   * Finds assets across multiple collins instances.  Data for instances are
   * stored as assets themselves, though the asset type and attribute for URI
   * info is user-configured.
   */
  def findMulti(page: PageParams, params: util.AttributeResolver.ResultTuple, afinder: AssetFinder, operation: Option[String], details: Boolean): Page[AssetView] = {
    val instanceFinder = AssetFinder(
      tag = None,
      status = None,
      createdAfter = None,
      createdBefore = None,
      updatedAfter = None,
      updatedBefore = None,
      assetType = Some(AssetType.Enum.withName(Config.getString("multicollins.instanceAssetType","DATA_CENTER").trim.toString))
    )
    val findLocations = Asset
      .find(PageParams(0,50,"ASC"), AttributeResolver.emptyResultTuple, instanceFinder)
      .items
      .collect{case a: Asset => a}
      .filter{_.tag != Config.getString("multicollins.thisInstance","NONE")}
    //iterate over the locations, sending requests to each one and aggregate their results
    val remoteClients = findLocations.flatMap { locationAsset => 
      locationAsset.getMetaAttribute("LOCATION").map{_.getValue} match {
        case None =>
          logger.warn("No location attribute for remote location asset %s".format(locationAsset.tag))
          None
        case Some(location) =>
          try {
            val remoteHost = RemoteCollinsHost(location)
            Some(new HttpRemoteAssetClient(locationAsset.tag, remoteHost))
          } catch {
            case e =>
              logger.error("Invalid location %s".format(e.getMessage))
              None
          }
      }
    }
    val (items, total) = RemoteAssetFinder(remoteClients :+ LocalAssetClient, page, AssetSearchParameters(params, afinder, operation, details))
    Page(items, page.page, page.offset,total)

  }

  /**
   * Finds assets in the same nodeclass as the given asset
   */
  def findSimilar(asset: Asset, page: PageParams, afinder: AssetFinder, sortType: SortType): Page[AssetView] = {
    val sorter = try SortDirection.withName(page.sort) catch {
      case _ => {
        logger.warn("Invalid sort " + page.sort)
        SortDirection.Desc
      }
    }
    asset.nodeClass.map{ nodeclass => 
      logger.debug("Asset %s has NodeClass %s".format(asset.tag, nodeclass.tag))
      val unsortedItems:Page[AssetView] = find(
        PageParams(0,10000, "asc"), //TODO: unbounded search
        (Nil, nodeclass.filteredMetaSeq, Nil),
        afinder,
        Some("and")
      )
      val sortedItems = AssetDistanceSorter.sort(
        asset, 
        unsortedItems.items.collect{case a: Asset => a}.filter{_.tag != asset.tag}, 
        sortType,
        sorter
      )
      val sortedPage: Page[AssetView] = unsortedItems.copy(items = sortedItems.slice(page.offset, page.offset + page.size), total = sortedItems.size)
      sortedPage
    }.getOrElse{
      logger.warn("No Nodeclass for Asset " + asset.tag)
      Page.emptyPage
    }
  }

  /**
   * Used only when repopulating the solr index, this should not be used anywhere else
   */
  def findRaw() = inTransaction { log {
    from(tableDef){asset => 
      where(AssetFinder.empty.asLogicalBoolean(asset))
      select(asset)
    }.toList
  }}

  def partialUpdate(asset: Asset, updated: Option[Timestamp], status: Option[Int]) = inTransaction {
    val oldAsset = Asset.findById(asset.id).get
    val res = if (updated.isDefined && status.isDefined) {
      tableDef.update (a =>
        where(a.id === asset.id)
        set(a.updated := updated, a.status := status.get)
      )
    } else if (updated.isDefined) {
      tableDef.update (a =>
        where(a.id === asset.id)
        set(a.updated := updated)
      )
    } else if (status.isDefined) {
      tableDef.update (a =>
        where(a.id === asset.id)
        set(a.status := status.get)
      )
    } else {
      throw new IllegalStateException("Neither updated or status were specified")
    }
    loggedInvalidation("partialUpdate", asset)
    val newAsset = Asset.findById(asset.id).get
    updateEventName.foreach { name =>
      oldAsset.forComparison
      newAsset.forComparison
      util.plugins.Callback.fire(name, oldAsset, newAsset)
    }
    res
  }

  case class AllAttributes(asset: Asset, lshw: LshwRepresentation, lldp: LldpRepresentation, ipmi: Option[IpmiInfo], addresses: Seq[IpAddresses], power: PowerUnits, mvs: Seq[MetaWrapper]) {
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

    def formatPowerUnits = JsArray(
      power.toList.map { unit =>
        JsObject(
          Seq(
            "UNIT_ID" -> JsNumber(unit.id),
            "UNITS" -> JsArray(unit.toList.map { component =>
              JsObject(Seq(
                "KEY" -> JsString(component.key),
                "VALUE" -> JsString(component.value.getOrElse("Unspecified")),
                "TYPE" -> JsString(component.identifier),
                "LABEL" -> JsString(component.label),
                "POSITION" -> JsNumber(component.position),
                "IS_REQUIRED" -> JsBoolean(component.isRequired),
                "UNIQUE" -> JsBoolean(component.isUnique)
              ))
            })
          )
        )
      }
    )

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
        "POWER" -> formatPowerUnits,
        "ATTRIBS" -> JsObject(mvs.groupBy { _.getGroupId }.map { case(groupId, mv) =>
          groupId.toString -> JsObject(mv.map { mvw => mvw.getName -> JsString(mvw.getValue) })
        }.toSeq)
      )
      JsObject(outSeq)
    }
  }
}
