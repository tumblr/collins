package models

import play.api.Configuration
import play.api.libs.json._
import util.{Helpers, IpAddress, IpAddressCalc}
import org.squeryl.dsl.ast.LogicalBoolean

case class IpAddresses(
  asset_id: Long,
  gateway: Long,
  address: Long,
  netmask: Long,
  id: Long = 0) extends IpAddressable
{
  override def asJson: String = {
    Json.stringify(JsObject(forJsonObject))
  }
  def forJsonObject(): Seq[(String,JsValue)] = Seq(
    "ID" -> JsNumber(getId()),
    "ASSET_ID" -> JsNumber(getAssetId()),
    "ADDRESS" -> JsString(dottedAddress),
    "GATEWAY" -> JsString(dottedGateway),
    "NETMASK" -> JsString(dottedNetmask)
  )
}

object IpAddresses extends IpAddressStorage[IpAddresses] {
  import org.squeryl.PrimitiveTypeMode._

  val tableDef = table[IpAddresses]("ip_addresses")
  on(tableDef)(i => declare(
    i.id is(autoIncremented,primaryKey),
    i.address is(unique),
    i.gateway is(indexed),
    i.netmask is(indexed),
    columns(i.asset_id, i.address) are(indexed)
  ))

  def createForAsset(asset: Asset, count: Int, scope: Option[String]): Seq[IpAddresses] = {
    (0 until count).map { i =>
      createForAsset(asset, scope)
    }
  }

  def createForAsset(asset: Asset, scope: Option[String]): IpAddresses = inTransaction {
    val assetId = asset.getId
    val (gateway, address, netmask) = getNextAvailableAddress()(scope)
    val ipAddresses = IpAddresses(assetId, gateway, address, netmask)
    tableDef.insert(ipAddresses)
  }

  protected[this] def generateFindQuery(addressRow: IpAddresses, address: String): LogicalBoolean = {
    try {
      if (address.split('.').size != 4) throw new Exception("Try again later")
      (addressRow.address === IpAddress.toLong(address))
    } catch {
      case e =>
        val padded = IpAddress.padRight(address, "0")
        val netmask = IpAddress.netmaskFromPad(padded, "0")
        val calc = IpAddressCalc(padded, netmask, None)
        (addressRow.address gte calc.minAddressAsLong) and
        (addressRow.address lte calc.maxAddressAsLong)
    }
  }

  def findAssetsByAddress(page: PageParams, addys: Seq[String], finder: AssetFinder): Page[Asset] = {
    def whereClause(assetRow: Asset, addressRow: IpAddresses) = {
      where(
        (assetRow.id === addressRow.asset_id) and
        generateFindQuery(addressRow, addys.head) and
        finder.asLogicalBoolean(assetRow)
      )
    }
    inTransaction {
      val results = from(Asset.tableDef, tableDef)((assetRow, addressRow) =>
        whereClause(assetRow, addressRow)
        select(assetRow)
      ).page(page.offset, page.size).toList
      val totalCount = from(Asset.tableDef, tableDef)((assetRow, addressRow) =>
        whereClause(assetRow, addressRow)
        compute(count)
      )
      Page(results, page.page, page.offset, totalCount)
    }
  }
  def findByAddress(address: String): Option[Asset] = inTransaction {
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
  }

  override def get(i: IpAddresses) = getOrElseUpdate(getKey.format(i.id)) {
    tableDef.lookup(i.id).get
  }

  override protected def getConfig()(implicit scope: Option[String]): Option[Configuration] = {
    Helpers.getConfig("ipAddresses").flatMap { config =>
      scope match {
        case None => Some(config)
        case Some(s) => config.getConfig(s)
      }
    }
  }

}
