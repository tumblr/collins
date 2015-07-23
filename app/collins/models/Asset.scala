package collins.models

import collins.models.asset.AssetView
import collins.models.asset.AllAttributes
import collins.models.asset.conversions._
import collins.models.conversions._
import collins.util.AttributeResolver
import collins.util.LldpRepresentation
import collins.util.LshwRepresentation
import collins.util.MessageHelper
import collins.util.Stats
import collins.util.config.Feature
import collins.util.config.MultiCollinsConfig
import collins.util.config.NodeclassifierConfig
import collins.util.power.PowerUnits
import collins.util.views.Formatter.dateFormat
import collins.solr.CQLQuery
import collins.solr.AssetDocType
import collins.solr.AssetSearchQuery
import collins.models.shared.QueryLogConfig
import collins.models.AssetSort.Type

import collins.validation.Pattern.isAlphaNumericString

import collins.util.{MessageHelper, RemoteCollinsHost, Stats}

import play.api.libs.json.Json
import play.api.Logger

import org.squeryl.PrimitiveTypeMode._
import org.squeryl.Schema
import org.squeryl.annotations.Column
import org.squeryl.annotations.Transient

import java.sql.Timestamp
import java.util.Date

import collins.models.shared.SortDirection._
import collins.models.shared.ValidatedEntity
import collins.models.shared.AnormAdapter
import collins.models.shared.Page
import collins.models.shared.PageParams
import collins.models.shared.SortDirection

case class Asset(tag: String, @Column("STATUS") statusId: Int, @Column("ASSET_TYPE") assetTypeId: Int,
    created: Timestamp, updated: Option[Timestamp], deleted: Option[Timestamp],
    id: Long = 0, @Column("STATE") stateId: Int = 0) extends ValidatedEntity[Long] with AssetView
{
  private[this] val logger = Logger("Asset")

  override def validate() {
    require(Asset.isValidTag(tag), "Tag must be non-empty alpha numeric")
  }
  override def asJson: String = toJsValue.toString

  @Transient
  override lazy val getHostnameMetaValue = getMetaAttributeValue("HOSTNAME")
  @Transient
  override def getPrimaryRoleMetaValue = getMetaAttributeValue("PRIMARY_ROLE")

  override def toJsValue() = Json.toJson[AssetView](this)

  @Transient
  lazy val status: Status = Status.findById(statusId).get
  @Transient
  override lazy val getStatusName: String = status.name

  @Transient
  lazy val state: Option[State] = State.findById(stateId)
  @Transient
  override lazy val getStateName: String = state.map(_.name).getOrElse("Unknown")

  @Transient
  lazy val assetType: AssetType = AssetType.findById(assetTypeId).get
  @Transient
  override lazy val getTypeName: String = assetType.name

  def getMetaAttributeValue(name: String): Option[String] = getMetaAttribute(name).map(_.getValue)

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
    import collins.util.AttributeResolver._
    val nodeclassType = NodeclassifierConfig.assetType
    val instanceFinder = AssetFinder
      .empty
      .copy ( 
        assetType = Some(nodeclassType)
      )
    val nodeclassParams: ResolvedAttributes = EmptyResolvedAttributes
      .withMeta(NodeclassifierConfig.identifyingMetaTag, "true")
    val nodeclasses = AssetMetaValue
      .findAssetsByMeta(PageParams(0,50,"ASC", "tag"), nodeclassParams.assetMeta, instanceFinder, Some("and"))
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
    a.statusId is(indexed),
    a.stateId is(indexed),
    a.assetTypeId is(indexed),
    a.created is(indexed),
    a.updated is(indexed)
  ))
  object Messages extends MessageHelper("asset") {
    def intakeError[T <: AssetView](t: String, a: T) = "intake.error.%s".format(t.toLowerCase) match {
      case msg if msg == "intake.error.new" =>
        messageWithDefault(msg, "Invalid asset status", a.getStatusName)
      case msg if msg == "intake.error.type" =>
        messageWithDefault(msg, "Invalid asset type", a.getTypeName)
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

  def apply(tag: String, status: Status, assetType: AssetType) = {
    new Asset(tag, status.id, assetType.id, new Date().asTimestamp, None, None)
  }

  override def delete(asset: Asset): Int = inTransaction {
    afterDeleteCallback(asset) {
      tableDef.deleteWhere(a => a.id === asset.id)
    }
  }

  def find(page: PageParams, params: AttributeResolver.ResultTuple, afinder: AssetFinder, operation: Option[String] = None): Page[AssetView] =
  Stats.time("Asset.find") {
    CQLQuery(AssetDocType, AssetSearchParameters(params, afinder, operation).toSolrExpression)
      .typeCheck
      .right
      .flatMap{exp => AssetSearchQuery(exp, page).getPage(findByTags(_))}
      .fold(
        err => throw new Exception(err),
        page => page
      )
  }

  def findById(id: Long) = inTransaction {
    tableDef.lookup(id)
  }
  def get(a: Asset) = findById(a.id).get

  def findByTags(tags: Seq[String]): List[Asset] = inTransaction {
    val ltags = tags.map { _.toLowerCase }
    from(tableDef)(s =>
      where(s.tag.toLowerCase in ltags)
      select(s)
    ).toList
  }
  
  def findByTag(tag: String): Option[Asset] = inTransaction {
    tableDef.where(_.tag.toLowerCase === tag.toLowerCase).headOption
  }

  /**
   * Finds assets across multiple collins instances.  Data for instances are
   * stored as assets themselves, though the asset type and attribute for URI
   * info is user-configured.
   */
  def findMulti(page: PageParams, params: AttributeResolver.ResultTuple, afinder: AssetFinder, operation: Option[String], details: Boolean): Page[AssetView] = {
    val instanceFinder = AssetFinder(
      tag = None,
      status = None,
      createdAfter = None,
      createdBefore = None,
      updatedAfter = None,
      updatedBefore = None,
      assetType = Some(MultiCollinsConfig.instanceAssetType),
      state = None,
      query = None
    )
    val findLocations = Asset
      .find(PageParams(0,50,"ASC", "tag"), AttributeResolver.emptyResultTuple, instanceFinder)
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
            case e: Throwable =>
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
  def findSimilar(asset: Asset, page: PageParams, afinder: AssetFinder, sortType: Type): Page[AssetView] = {
    val sorter = SortDirection.withName(page.sort.toString).getOrElse( {
      logger.warn("Invalid sort " + page.sort)
      SortDesc
    })
    asset.nodeClass.map{ nodeclass => 
      logger.debug("Asset %s has NodeClass %s".format(asset.tag, nodeclass.tag))
      val unsortedItems:Page[AssetView] = find(
        PageParams(0,10000, "asc", "tag"), //TODO: unbounded search
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
      where(a.stateId === state.id)
      set(a.stateId := newId)
    )
    // We repopulate solr because the alternative is to do some complex state tracking
    // The above update operation also will not trigger callbacks
    Solr.populate()
    count
  }

  def partialUpdate(asset: Asset, updated: Option[Timestamp], status: Option[Int], state: Option[State] = None) = {
    val assetWUpdate = updated.map(u => asset.copy(updated = Some(u))).getOrElse(asset)
    val assetWStatus = status.map(s => assetWUpdate.copy(statusId = s)).getOrElse(assetWUpdate)
    val assetWState = state.map(s => assetWStatus.copy(stateId = s.id)).getOrElse(assetWStatus)
    Asset.update(assetWState)
  }

}
