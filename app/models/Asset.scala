package models

import asset.{AssetView, AllAttributes}
import asset.conversions._
import conversions._
import util.{AttributeResolver, LldpRepresentation, LshwRepresentation, MessageHelper, Stats}
import util.config.{Feature, MultiCollinsConfig, NodeclassifierConfig}
import util.plugins.Cache
import util.power.PowerUnits
import util.views.Formatter.dateFormat
import collins.solr._
import shared.QueryLogConfig
import AssetSortType.AssetSortType

import collins.validation.Pattern.isAlphaNumericString

import util.{MessageHelper, RemoteCollinsHost, Stats}

import play.api.libs.json.Json
import play.api.Logger

import org.squeryl.PrimitiveTypeMode._
import org.squeryl.Schema

import java.sql.Timestamp
import java.util.Date

import SortDirection._

case class Asset(tag: String, status: Int, asset_type: Int,
    created: Timestamp, updated: Option[Timestamp], deleted: Option[Timestamp],
    id: Long = 0, state: Int = 0) extends ValidatedEntity[Long] with AssetView
{
  private[this] val logger = Logger("Asset")

  override def validate() {
    require(Asset.isValidTag(tag), "Tag must be non-empty alpha numeric")
  }
  override def asJson: String = toJsValue.toString

  override def getHostnameMetaValue = getMetaAttribute("HOSTNAME").map(_.getValue)
  override def getPrimaryRoleMetaValue = getMetaAttribute("PRIMARY_ROLE").map(_.getValue)
  override def toJsValue() = {
    Json.toJson[AssetView](this)
  }

  def getId(): Long = id

  def getStatus(): Status = Status.findById(status).get
  override def getStatusName(): String = getStatus().name

  def getState(): Option[State] = State.findById(state)

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

  private[this] val logger = Logger("Asset")
  override protected val createEventName = Some("asset_create")
  override protected val updateEventName = Some("asset_update")
  override protected val deleteEventName = Some("asset_delete")

  override val tableDef = table[Asset]("asset")
  on(tableDef)(a => declare(
    a.id is(autoIncremented,primaryKey),
    a.tag is(unique),
    a.status is(indexed),
    a.state is(indexed),
    a.asset_type is(indexed),
    a.created is(indexed),
    a.updated is(indexed)
  ))
  override def cacheKeys(asset: Asset) = Seq(
    "Asset.findByTag(%s)".format(asset.tag.toLowerCase),
    "Asset.findById(%d)".format(asset.id)
  )
  def flushCache(asset: Asset) = loggedInvalidation("flushCache", asset)
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

  def isValidTag(tag: String): Boolean = isAlphaNumericString(tag)

  def apply(tag: String, status: Status, asset_type: AssetType) = {
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

  def findByTag(tag: String): Option[Asset] = {
    getOrElseUpdate("Asset.findByTag(%s)".format(tag.toLowerCase)) {
      tableDef.where(a => a.tag.toLowerCase === tag.toLowerCase).headOption
    }
  }

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
      assetType = Some(MultiCollinsConfig.instanceAssetType),
      state = None
    )
    val findLocations = Asset
      .find(PageParams(0,50,"ASC"), AttributeResolver.emptyResultTuple, instanceFinder)
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
    val sorter = SortDirection.withName(page.sort.toString).getOrElse( {
      logger.warn("Invalid sort " + page.sort)
      SortDesc
    })
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
      val sortedPage: Page[AssetView] = Page(
        page = page.page, 
        items = sortedItems.slice(page.offset, page.offset + page.size), 
        total = sortedItems.size, 
        offset = page.offset
      )
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

  def resetState(state: State, newId: Int): Int = inTransaction {
    import collins.solr.Solr
    val count = tableDef.update(a =>
      where(a.state === state.id)
      set(a.state := newId)
    )
    // We repopulate solr because the alternative is to do some complex state tracking
    // The above update operation also will not trigger callbacks
    Solr.populate()
    count
  }

  def partialUpdate(asset: Asset, updated: Option[Timestamp], status: Option[Int], state: Option[State] = None) = {
    val assetWUpdate = updated.map(u => asset.copy(updated = Some(u))).getOrElse(asset)
    val assetWStatus = status.map(s => assetWUpdate.copy(status = s)).getOrElse(assetWUpdate)
    val assetWState = state.map(s => assetWStatus.copy(state = s.id)).getOrElse(assetWStatus)
    Asset.update(assetWState)
  }

}
