package models

import shared.{AddressPool, IpAddressConfiguration}

import play.api.Configuration
import play.api.libs.json._
import util.{Config, IpAddress, IpAddressCalc}
import util.plugins.Callback
import util.plugins.solr.Solr
import org.squeryl.dsl.ast.LogicalBoolean

case class IpAddresses(
  asset_id: Long,
  gateway: Long,
  address: Long,
  netmask: Long,
  pool: String,
  id: Long = 0) extends IpAddressable
{
  override def asJson: String = {
    Json.stringify(JsObject(forJsonObject))
  }
  def toJsonObject() = JsObject(forJsonObject)
  def forJsonObject(): Seq[(String,JsValue)] = Seq(
    "ID" -> JsNumber(getId()),
    "ASSET_ID" -> JsNumber(getAssetId()),
    "ADDRESS" -> JsString(dottedAddress),
    "GATEWAY" -> JsString(dottedGateway),
    "NETMASK" -> JsString(dottedNetmask),
    "POOL" -> JsString(pool)
  )
}

object IpAddresses extends IpAddressStorage[IpAddresses] with IpAddressCacheManagement {
  import org.squeryl.PrimitiveTypeMode._

  override protected def createEventName: Option[String] = Some("ipAddresses_create")
  override protected def updateEventName: Option[String] = Some("ipAddresses_update")
  override protected def deleteEventName: Option[String] = Some("ipAddresses_delete")

  lazy val AddressConfig = IpAddressConfiguration(Config.get("ipAddresses"))

  val tableDef = table[IpAddresses]("ip_addresses")
  on(tableDef)(i => declare(
    i.id is(autoIncremented,primaryKey),
    i.address is(unique),
    i.gateway is(indexed),
    i.netmask is(indexed),
    i.pool is(indexed),
    columns(i.asset_id, i.address) are(indexed)
  ))

  def createForAsset(asset: Asset, count: Int, scope: Option[String]): Seq[IpAddresses] = {
    (0 until count).map { i =>
      createForAsset(asset, scope)
    }
  }

  override def getNextAvailableAddress(overrideStart: Option[String] = None)(implicit scope: Option[String]): Tuple3[Long,Long,Long] = {
    throw new UnsupportedOperationException("getNextAvailableAddress not supported")
  }

  def getNextAddress(iteration: Int)(implicit scope: Option[String]): Tuple3[Long,Long,Long] = {
    val network = getNetwork
    val startAt = getStartAddress
    val calc = IpAddressCalc(network, startAt)
    val gateway: Long = getGateway().getOrElse(calc.minAddressAsLong)
    val netmask: Long = calc.netmaskAsLong
    val currentMax: Option[Long] = iteration match {
      case norm if norm == 0 =>
        nextAddressFromCache(scope)
      case failed =>
        logger.info("Address cache dirty. Failed cache lookup so using DB")
        populateCacheIfNeeded(scope, true)
        getCurrentMaxAddress(calc.minAddressAsLong, calc.maxAddressAsLong)
    }
    val address: Long = calc.nextAvailableAsLong(currentMax)
    (gateway, address, netmask)
  }

  def createForAsset(asset: Asset, scope: Option[String]): IpAddresses = inTransaction {
    val assetId = asset.getId
    val cfg = getConfig()(scope)
    val ipAddresses = createWithRetry(10) { attempt =>
      val (gateway, address, netmask) = getNextAddress(attempt)(scope)
      logger.debug("trying to use address %s".format(IpAddress.toString(address)))
      val ipAddresses = IpAddresses(assetId, gateway, address, netmask, scope.getOrElse(""))
      super.create(ipAddresses)
    }
    Solr.updateAsset(asset)
    ipAddresses
  }

  def deleteByAssetAndPool(asset: Asset, pool: Option[String]): Int = inTransaction {
    val rows = tableDef.where(i =>
      i.asset_id === asset.id and
      i.pool === pool.?
    ).toList
    val res = rows.foldLeft(0) { case(sum, ipInfo) =>
      sum + delete(ipInfo)
    }
    Solr.updateAsset(asset)
    res
  }

  def findAssetsByAddress(page: PageParams, addys: Seq[String], finder: AssetFinder): Page[AssetView] = {
    def whereClause(assetRow: Asset, addressRow: IpAddresses) = {
      where(
        (assetRow.id === addressRow.asset_id) and
        generateFindQuery(addressRow, addys.head) and
        finder.asLogicalBoolean(assetRow)
      )
    }
    inTransaction { log {
      val results = from(Asset.tableDef, tableDef)((assetRow, addressRow) =>
        whereClause(assetRow, addressRow)
        select(assetRow)
      ).page(page.offset, page.size).toList
      val totalCount = from(Asset.tableDef, tableDef)((assetRow, addressRow) =>
        whereClause(assetRow, addressRow)
        compute(count)
      )
      Page(results, page.page, page.offset, totalCount)
    }}
  }

  def findByAddress(address: String): Option[Asset] = inTransaction { log {
    val addressAsLong = try {
      IpAddress.toLong(address)
    } catch {
      case e => return None
    }
    from(tableDef, Asset.tableDef)((i,a) =>
      where(
        (i.address === addressAsLong) and
        (i.asset_id === a.id)
      )
      select(a)
    ).headOption
  }}

  def findInPool(pool: String): Seq[IpAddresses] = inTransaction { log {
    from(tableDef)(i =>
      where(i.pool === pool)
      select(i)
    ).toList
  }}

  override def get(i: IpAddresses) = getOrElseUpdate(getKey.format(i.id)) {
    tableDef.lookup(i.id).get
  }

  def getPoolsInUse(): Set[String] = getOrElseUpdate("getPoolsInUse") {
    from(tableDef)(i =>
      select(i.pool)
    ).distinct.toSet
  }

  override protected def getConfig()(implicit scope: Option[String]): Option[AddressPool] = {
    AddressConfig.flatMap { cfg =>
      scope.flatMap(cfg.pool(_)).orElse(cfg.defaultPool)
    }
  }

  protected def populateCacheIfNeeded(opool: Option[String], force: Boolean = false) {
    opool.foreach { pool =>
      AddressConfig.flatMap(_.pool(pool)).foreach { ap =>
        if (!ap.hasAddressCache || force) {
          logger.trace("populating address cache for pool %s".format(ap.name))
          findInPool(pool).foreach { address =>
            try ap.useAddress(address.address) catch {
              case e =>
                logger.info("Error using address %s in pool %s".format(address.dottedAddress, pool))
            }
          }
        }
      }
    }
  }

  // NOTE if we find an unused address, and for some reason it is already in the DB, and the insert
  // fails, we will recommend the exact same address for use. This happened when the
  // ipAddresses_create callback wasn't working correctly. We need a way to detect that we're trying
  // to use the same IP address
  protected def nextAddressInPool(opool: Option[String]): Option[Long] = {
    logger.trace("finding next address in pool %s".format(opool.getOrElse("UNKNOWN")))
    opool.flatMap { pool =>
      try {
        logger.trace("found pool %s, looking for config".format(pool))
        AddressConfig.flatMap(_.pool(pool)).map { ap =>
          logger.debug("Next address in pool %s in cache is %s".format(ap.name, ap.nextDottedAddress))
          ap.nextAddress - 1
        }
      } catch {
        case e =>
          logger.warn("Exception getting next address: %s".format(e))
          None
      }
    }
  }

  protected def nextAddressFromCache(scope: Option[String]): Option[Long] = {
    populateCacheIfNeeded(scope)
    nextAddressInPool(scope)
  }

  protected[this] def generateFindQuery(addressRow: IpAddresses, address: String): LogicalBoolean = {
    try {
      if (address.split('.').size != 4) throw new Exception("Try again later")
      (addressRow.address === IpAddress.toLong(address))
    } catch {
      case e =>
        try {
          val padded = IpAddress.padRight(address, "0")
          val netmask = IpAddress.netmaskFromPad(padded, "0")
          val calc = IpAddressCalc(padded, netmask, None)
          (addressRow.address gte calc.minAddressAsLong) and
          (addressRow.address lte calc.maxAddressAsLong)
        } catch {
          case ev =>
            logger.warn("Totally invalid address: %s".format(address), e)
            throw e
        }
    }
  }
}

trait IpAddressCacheManagement { self: IpAddressStorage[IpAddresses] =>
  // Callbacks to manage populating and managing the address caches
  Callback.on("ipAddresses_create") { pce =>
    val newAddress = pce.getNewValue.asInstanceOf[IpAddresses]
    logger.debug("ipAddress_create pool %s".format(newAddress.pool))
    IpAddresses.AddressConfig.flatMap(_.pool(newAddress.pool)).foreach { ap =>
      logger.debug("Using address %s in pool %s".format(newAddress.dottedAddress, ap.name))
      ap.useAddress(newAddress.address)
    }
  }
  Callback.on("ipAddresses_update") { pce =>
    val oldAddress = pce.getOldValue.asInstanceOf[IpAddresses]
    val newAddress = pce.getNewValue.asInstanceOf[IpAddresses]
    IpAddresses.AddressConfig.flatMap(_.pool(oldAddress.pool)).foreach { ap =>
      logger.debug("Purging address %s from pool %s".format(oldAddress.dottedAddress, ap.name))
      ap.unuseAddress(oldAddress.address)
    }
    IpAddresses.AddressConfig.flatMap(_.pool(newAddress.pool)).foreach { ap =>
      logger.debug("Using address %s in pool %s".format(newAddress.dottedAddress, ap.name))
      ap.useAddress(newAddress.address)
    }
  }
  Callback.on("ipAddresses_delete") { pce =>
    val oldAddress = pce.getOldValue.asInstanceOf[IpAddresses]
    IpAddresses.AddressConfig.flatMap(_.pool(oldAddress.pool)).foreach { ap =>
      logger.debug("Purging address %s from pool %s".format(oldAddress.dottedAddress, ap.name))
      ap.unuseAddress(oldAddress.address)
    }
  }
}


