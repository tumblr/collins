package models

import play.api.Configuration
import play.api.libs.json._
import util.{Helpers, IpAddress}

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

  def createForAsset(asset: Asset, count: Int): Seq[IpAddresses] = {
    (0 until count).map { i =>
      createForAsset(asset)
    }
  }

  def createForAsset(asset: Asset): IpAddresses = inTransaction {
    val assetId = asset.getId
    val (gateway, address, netmask) = getNextAvailableAddress()
    val ipAddresses = IpAddresses(assetId, gateway, address, netmask)
    tableDef.insert(ipAddresses)
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

  override protected def getConfig(): Option[Configuration] = {
    Helpers.getConfig("ipAddresses")
  }

}
