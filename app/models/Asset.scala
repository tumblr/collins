package models

import asset.{AssetView, AllAttributes}
import asset.conversions._
import conversions._
import shared.QueryLogConfig
import AssetSortType.AssetSortType

import util.{MessageHelper, RemoteCollinsHost, Stats}
import util.config.{MultiCollinsConfig, NodeclassifierConfig}
import util.plugins.Cache

import play.api.libs.json.Json
import play.api.Logger

import org.squeryl.PrimitiveTypeMode._
import org.squeryl.Schema

import java.sql.Timestamp
import java.util.Date

case class Asset(tag: String, status: Int, asset_type: Int,
    created: Timestamp, updated: Option[Timestamp], deleted: Option[Timestamp],
    id: Long = 0) extends ValidatedEntity[Long] with AssetView
{
  private[this] val logger = Logger("Asset")

  override def validate() {
    require(Asset.isValidTag(tag), "Tag must be non-empty alpha numeric")
  }
  override def asJson: String = toJsValue.toString

  override def getHostnameMetaValue = getMetaAttribute("HOSTNAME").map{_.getValue}
  override def getPrimaryRoleMetaValue = getMetaAttribute("PRIMARY_ROLE").map{_.getValue}
  override def toJsValue() = {
    Json.toJson[AssetView](this)
  }

  def getId(): Long = id

  def getStatus(): Status = Status.findById(status).get

  override def getStatusName(): String = getStatus().name

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

  def getAllAttributes: AllAttributes = AllAttributes.get(this)

  /**
   * Returns the nodeclass of this asset.  Nodeclass is found by getting all
   * nodeclass assets and finding the first one whose meta values match the values
   * of this asset
   */
  def nodeClass: Option[Asset] = {
    import util.AttributeResolver._
    val nodeclassType = NodeclassifierConfig.assetType
    val instanceFinder = AssetFinder
      .empty
      .copy ( 
        assetType = Some(nodeclassType)
      )
    val nodeclassParams: ResolvedAttributes = EmptyResolvedAttributes
      .withMeta(NodeclassifierConfig.identifyingMetaTag, "true")
    val nodeclasses = AssetMetaValue
      .findAssetsByMeta(PageParams(0,50,"ASC"), nodeclassParams.assetMeta, instanceFinder, Some("and"))
      .items
      .collect{case a: Asset => a}
    val myMetaSeq = this.metaSeq
    //Note - we cannot use set operations because an asset may contain multiple values of the same meta
    nodeclasses.map{n => 
      val metaseq = n.filteredMetaSeq
      if (metaseq.foldLeft(true){(ok, metaval) => ok && (myMetaSeq contains metaval)}) {
        logger.debug("%s,%d".format(n.toString, metaseq.size))
        Some(n -> metaseq.size)
      } else {
        logger.debug("%s,NONE".format(n.toString))
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
    val excludeMetaTags = NodeclassifierConfig.excludeMetaTags
    metaSeq.filter{case(meta, value) => !(excludeMetaTags contains meta.name)}
  }

  def remoteHost = None
}

object Asset extends Schema with AnormAdapter[Asset] {

  private[this] val TagR = """[A-Za-z0-9\-_]+""".r.pattern.matcher(_)
  private[this] val logger = Logger("Asset")
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
  def flushCache(asset: Asset) = cacheKeys(asset).foreach { k =>
    Cache.invalidate(k)
  }
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

  def find(page: PageParams, params: util.AttributeResolver.ResultTuple, afinder: AssetFinder, operation: Option[String] = None): Page[AssetView] =
  Stats.time("Asset.find") {
    val results = (if (params._1.nonEmpty) {
      IpmiInfo.findAssetsByIpmi(page, params._1, afinder)
    } else if (params._2.nonEmpty) {
      AssetMetaValue.findAssetsByMeta(page, params._2, afinder, operation)
    } else if (params._3.nonEmpty) {
      IpAddresses.findAssetsByAddress(page, params._3, afinder)
    } else {
      Asset.find(page, afinder)
    })
    //log the frontend query as a query string with result count
    if (QueryLogConfig.frontendLogging) {
      logger.info("API_QUERY:" + AssetSearchParameters(params, afinder, operation).toQueryString.getOrElse("(empty)") + ":" + results.total)
    }
    results
  }

  def find(page: PageParams, afinder: AssetFinder): Page[AssetView] = inTransaction { log {
    val results = from(tableDef)(a =>
      where(afinder.asLogicalBoolean(a))
      select(a)
    ).page(page.offset, page.size).toList
    val totalCount = from(tableDef)(a =>
      where(afinder.asLogicalBoolean(a))
      compute(count)
    )
    Page(results, page.page, page.offset, totalCount)
  }}

  def find(page: PageParams, finder: AssetFinder, assets: Set[Long]): Page[AssetView] = inTransaction { log {
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
  }}

  def findById(id: Long) = getOrElseUpdate("Asset.findById(%d)".format(id)) {
    tableDef.lookup(id)
  }
  def get(a: Asset) = findById(a.id).get

  def findByTag(tag: String): Option[Asset] = {
    getOrElseUpdate("Asset.findByTag(%s)".format(tag.toLowerCase)) {
      tableDef.where(a => a.tag.toLowerCase === tag.toLowerCase).headOption
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
      assetType = Some(MultiCollinsConfig.instanceAssetType)
    )
    val findLocations = Asset
      .find(PageParams(0,50,"ASC"), instanceFinder)
      .items
      .collect{case a: Asset => a}
      .filter{_.tag != MultiCollinsConfig.thisInstance}
    //iterate over the locations, sending requests to each one and aggregate their results
    val remoteClients = findLocations.flatMap { locationAsset => 
      val locationAttribute = MultiCollinsConfig.locationAttribute
      locationAsset.getMetaAttribute(locationAttribute).map(_.getValue) match {
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
  def findSimilar(asset: Asset, page: PageParams, afinder: AssetFinder, sortType: AssetSortType): Page[AssetView] = {
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

}
