package models

import conversions._
import util.{Config, Feature, LldpRepresentation, LshwRepresentation, MessageHelper, Stats}
import util.power.PowerUnits
import util.views.Formatter.dateFormat

import play.api.Logger
import play.api.libs.json._
import play.api.libs.ws.WS
import play.api._
import play.api.mvc._

import org.squeryl.Schema
import org.squeryl.PrimitiveTypeMode._

import java.sql.Timestamp
import java.util.Date
import java.text.SimpleDateFormat

import akka.dispatch.Await
import akka.util.duration._

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
  

}

/**
 * An asset controlled by another collins instance, used during multi-collins
 * searching
 */
case class RemoteAsset(_host: String, json: JsObject) extends AssetView {

  
  private[this] def jsonDateToTimestamp(j: JsValue): Option[Timestamp] = {
    val formatter = new SimpleDateFormat(util.views.Formatter.ISO_8601_FORMAT)
    j.asOpt[String].filter{_ != ""}.map{s => new Timestamp(formatter.parse(s).getTime)}
  }

  def toJsonObject = JsObject(forJsonObject)
  def forJsonObject = json.fields :+ ("LOCATION" -> JsString(_host))

  def tag = (json \ "TAG").as[String]
  def created = jsonDateToTimestamp(json \ "CREATED").getOrElse(new Timestamp(0))
  def updated = jsonDateToTimestamp(json \ "UPDATED")

  def getHostnameMetaValue() = (json \ "ATTRIBS" \ "0" \ "HOSTNAME").asOpt[String]
  def getPrimaryRoleMetaValue() = (json \ "ATTRIBS" \ "0" \ "PRINARY_ROLE").asOpt[String]
  def getStatusName() = (json \ "STATUS" ).asOpt[String].getOrElse("Unknown")

  def remoteHost = Some(_host)

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
    val (lshwRep, mvs) = LshwHelper.reconstruct(this)
    val (lldpRep, mvs2) = LldpHelper.reconstruct(this, mvs)
    val ipmi = IpmiInfo.findByAsset(this)
    val addresses = IpAddresses.findAllByAsset(this)
    val (powerRep, mvs3) = PowerHelper.reconstruct(this, mvs2)
    val filtered: Seq[MetaWrapper] = mvs3.filter(f => !AssetConfig.HiddenMeta.contains(f.getName))
    Asset.AllAttributes(this, lshwRep, lldpRep, ipmi, addresses, powerRep, filtered)
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

  def find(page: PageParams, params: util.AttributeResolver.ResultTuple, afinder: AssetFinder, operation: Option[String] = None): Page[AssetView] =
  Stats.time("Asset.find") {
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

  def find(page: PageParams, afinder: AssetFinder): Page[AssetView] = inTransaction {
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

  def find(page: PageParams, finder: AssetFinder, assets: Set[Long]): Page[AssetView] = inTransaction {
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

  def findLikeTag(tag: String, params: PageParams): Page[AssetView] = inTransaction {
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

  /**
   * Finds assets across multiple collins instances.  Data for instances are
   * stored as assets themselves, though the asset type and attribute for URI
   * info is user-configured.
   */
  def findMulti(page: PageParams, params: util.AttributeResolver.ResultTuple, afinder: AssetFinder, operation: Option[String] = None): Page[AssetView] = {
    val instanceFinder = AssetFinder(
      tag = None,
      status = None,
      createdAfter = None,
      createdBefore = None,
      updatedAfter = None,
      updatedBefore = None,
      assetType = Some(AssetType.Enum.withName(Config.getString("multicollins.instanceAssetType","DATA_CENTER").trim.toString))
    )
    val findLocations = Asset.find(PageParams(0,50,"ASC"), instanceFinder).items.collect{case a: Asset => a}
    //iterate over the locations, sending requests to each one and aggregate their results
    val remoteAssets = findLocations.map{ locationAsset => 
      locationAsset.getMetaAttribute("LOCATION").map{_.getValue} match {
        case None => {
          logger.warn("No location attribute for remote location asset %s".format(locationAsset.tag))
          Nil
        }
        case Some(location) => {
          val pieces = location.split(";")
          if (pieces.length < 2) {
            logger.error("Invalid location %s".format(location))
            Nil
          } else {
            val host = pieces(0) 
            val queryUrl = host + app.routes.Api.getAssets().toString
            val userpass = pieces(1).split(":")
            if (userpass.length != 2) {
              logger.error("Invalid user/pass %s for remote collins asset %s".format(pieces(1), locationAsset.id.toString))
              Nil
            } else {
              val authenticationTuple = (userpass(0), userpass(1), com.ning.http.client.Realm.AuthScheme.BASIC)
              //we have to rebuild the query
              logger.debug(params.toString)
              val queryString = {
                val q1: Map[String, String] = (
                  params._1.map{case (enum, value) => (enum.toString, value)} ++ 
                  params._2.map{case (assetMeta,value) => ("attribute" -> "%s;%s".format(assetMeta.name, value))} ++ 
                  params._3.map{i => ("ip_address" -> i)}
                ).toMap ++ afinder.toMap
                operation.map{op => q1 + ("operation" -> op)}.getOrElse(q1)
              }
              val request = WS.url(queryUrl).copy(
                queryString = queryString,
                auth = Some(authenticationTuple)
              )
              logger.debug("Here is our query string: " + queryString.toString)
              val result = request.get.await.get
              logger.debug(result.body)
              val json = Json.parse(result.body)
              (json \ "data" \ "Data") match {
                case JsArray(items) => items.map{
                  case obj: JsObject => Some(new RemoteAsset(host, obj))
                  case _ => {
                    logger.warn("Invalid asset in response data")
                    None
                  }
                }.flatten
                case _ => {
                  logger.warn("Invalid response from %s".format(host))
                  Nil
                }
              }
            }
          }
        }
      }
    }.flatten
    Page(find(page,params,afinder,operation).items ++ remoteAssets, page.page, page.offset,0)

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
