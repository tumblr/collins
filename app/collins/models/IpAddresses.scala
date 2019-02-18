package collins.models

import org.squeryl.PrimitiveTypeMode._
import org.squeryl.annotations.Column
import org.squeryl.dsl.ast.LogicalBoolean

import play.api.libs.json.Json

import collins.models.asset.AssetView
import collins.models.cache.Cache
import collins.models.shared.AddressPool
import collins.models.shared.IpAddressConfig
import collins.models.shared.IpAddressStorage
import collins.models.shared.IpAddressable
import collins.models.shared.Page
import collins.models.shared.PageParams
import collins.util.IpAddress
import collins.util.IpAddressCalc

import conversions.IpAddressFormat

case class IpAddresses(
    @Column("ASSET_ID") assetId: Long,
    gateway: Long,
    address: Long,
    netmask: Long,
    pool: String,
    id: Long = 0) extends IpAddressable {
  import conversions._
  override def asJson: String = toJsValue.toString
  def toJsValue = Json.toJson(this)

  override def compare(z: Any): Boolean = {
    if (z == null)
      return false
    val ar = z.asInstanceOf[AnyRef]
    if (!ar.getClass.isAssignableFrom(this.getClass))
      false
    else {
      val other = ar.asInstanceOf[IpAddresses]
      this.assetId == other.assetId && this.gateway == other.gateway && this.netmask == other.netmask &&
        this.pool == other.pool && this.id == other.id
    }
  }
}

object IpAddresses extends IpAddressStorage[IpAddresses] with IpAddressKeys[IpAddresses] {
  import org.squeryl.PrimitiveTypeMode._

  override def storageName = "IpAddresses"

  override protected def createEventName: Option[String] = Some("ipAddresses_create")
  override protected def updateEventName: Option[String] = Some("ipAddresses_update")
  override protected def deleteEventName: Option[String] = Some("ipAddresses_delete")

  val tableDef = table[IpAddresses]("ip_addresses")
  lazy val AddressConfig = IpAddressConfig.get()

  on(tableDef)(i => declare(
    i.id is (autoIncremented, primaryKey),
    i.address is (unique),
    i.gateway is (indexed),
    i.netmask is (indexed),
    i.pool is (indexed),
    columns(i.assetId, i.address) are (indexed)))

  def createForAsset(asset: Asset, count: Int, scope: Option[String]): Seq[IpAddresses] = {
    (0 until count).map { i =>
      createForAsset(asset, scope)
    }
  }

  override def getNextAvailableAddress(scope: Option[String], overrideStart: Option[String] = None): Tuple3[Long, Long, Long] = {
    throw new UnsupportedOperationException("getNextAvailableAddress not supported")
  }

  def getNextAddress(iteration: Int, scope: Option[String]): Tuple3[Long, Long, Long] = {
    val network = getNetwork(scope)
    val startAt = getStartAddress(scope)
    val calc = IpAddressCalc(network, startAt)
    val gateway: Long = getGateway(scope).getOrElse(calc.minAddressAsLong)
    val netmask: Long = calc.netmaskAsLong
    val currentMax: Option[Long] = getCurrentLowestLocalMaxAddress(calc, scope)
    val address: Long = calc.nextAvailableAsLong(currentMax)
    (gateway, address, netmask)
  }

  def createForAsset(asset: Asset, scope: Option[String]): IpAddresses = inTransaction {
    val assetId = asset.id
    val cfg = getConfig(scope)
    val ipAddresses = createWithRetry(10) { attempt =>
      val (gateway, address, netmask) = getNextAddress(attempt, scope)
      logger.debug("trying to use address %s".format(IpAddress.toString(address)))
      val ipAddresses = IpAddresses(assetId, gateway, address, netmask, scope.getOrElse(""))
      super.create(ipAddresses)
    }
    ipAddresses
  }

  def deleteByAssetAndPool(asset: Asset, pool: Option[String]): Int = inTransaction {
    val rows = tableDef.where(i =>
      i.assetId === asset.id and
        i.pool === pool.?).toList
    val res = rows.foldLeft(0) {
      case (sum, ipInfo) =>
        sum + delete(ipInfo)
    }
    res
  }

  def deleteByAssetAndAddress(asset: Asset, address: Option[String]): Int = inTransaction {
    val addressAsLong = try {
      IpAddress.toLong(address.getOrElse(""))
    } catch {
      case e: Throwable => return 0
    }
    val rows = tableDef.where(i =>
      i.assetId === asset.id and
        i.address === addressAsLong).toList
    val res = rows.foldLeft(0) {
      case (sum, ipInfo) =>
        sum + delete(ipInfo)
    }
    res
  }

  def findAssetsByAddress(page: PageParams, addys: Seq[String], finder: AssetFinder): Page[AssetView] = {
    def whereClause(assetRow: Asset, addressRow: IpAddresses) = {
      where(
        (assetRow.id === addressRow.assetId) and
          generateFindQuery(addressRow, addys.head) and
          finder.asLogicalBoolean(assetRow))
    }
    inTransaction {
      log {
        val results = from(Asset.tableDef, tableDef)((assetRow, addressRow) =>
          whereClause(assetRow, addressRow)
            select (assetRow)).page(page.offset, page.size).toList
        val totalCount = from(Asset.tableDef, tableDef)((assetRow, addressRow) =>
          whereClause(assetRow, addressRow)
            compute (count))
        Page(results, page.page, page.offset, totalCount)
      }
    }
  }

  def findByAddress(address: String): Option[Asset] = inTransaction {
    log {
      val addressAsLong = try {
        IpAddress.toLong(address)
      } catch {
        case e: Throwable => return None
      }
      from(tableDef, Asset.tableDef)((i, a) =>
        where(
          (i.address === addressAsLong) and
            (i.assetId === a.id))
          select (a)).headOption
    }
  }

  def findInPool(pool: String): List[IpAddresses] = inTransaction {
    log {
      from(tableDef)(i =>
        where(i.pool === pool)
          select (i)).toList
    }
  }

  override def get(i: IpAddresses) = Cache.get(findByIdKey(i.id), inTransaction {
    tableDef.lookup(i.id).get
  })

  def getPoolsInUse(): Set[String] = Cache.get(findPoolsInUseKey, inTransaction {
    from(tableDef)(i =>
      select(i.pool)).distinct.toSet
  })

  override protected def getConfig(scope: Option[String]): Option[AddressPool] = {
    AddressConfig.flatMap(cfg => scope.flatMap(cfg.pool(_)).orElse(cfg.defaultPool))
  }

  protected[this] def generateFindQuery(addressRow: IpAddresses, address: String): LogicalBoolean = {
    try {
      if (address.split('.').size != 4) throw new Exception("Try again later")
      (addressRow.address === IpAddress.toLong(address))
    } catch {
      case e: Throwable =>
        try {
          val padded = IpAddress.padRight(address, "0")
          val netmask = IpAddress.netmaskFromPad(padded, "0")
          val calc = IpAddressCalc(padded, netmask, None)
          (addressRow.address gte calc.minAddressAsLong) and
            (addressRow.address lte calc.maxAddressAsLong)
        } catch {
          case _: Throwable =>
            logger.warn("Totally invalid address: %s".format(address), e)
            throw e
        }
    }
  }
}

