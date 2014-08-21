package models

import asset.AssetView
import shared.{AddressPool, IpAddressConfig}

import play.api.libs.json._
import util.{IpAddress, IpAddressCalc}
import util.plugins.Callback
import org.squeryl.dsl.ast.LogicalBoolean

case class IpAddresses(
  asset_id: Long,
  gateway: Long,
  address: Long,
  netmask: Long,
  pool: String,
  id: Long = 0) extends IpAddressable
{
  import conversions._
  override def asJson: String = toJsValue.toString
  def toJsValue = Json.toJson(this)
}

object IpAddresses extends IpAddressStorage[IpAddresses] {
  import org.squeryl.PrimitiveTypeMode._

  override protected def createEventName: Option[String] = Some("ipAddresses_create")
  override protected def updateEventName: Option[String] = Some("ipAddresses_update")
  override protected def deleteEventName: Option[String] = Some("ipAddresses_delete")

  val tableDef = table[IpAddresses]("ip_addresses")
  lazy val AddressConfig = IpAddressConfig.get()

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
    val currentMax: Option[Long] = getCurrentLowestLocalMaxAddress(calc)
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
    AddressConfig.flatMap(cfg => scope.flatMap(cfg.pool(_)).orElse(cfg.defaultPool))
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

