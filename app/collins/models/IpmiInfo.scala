package collins.models

import org.squeryl.PrimitiveTypeMode._
import org.squeryl.dsl.ast.BinaryOperatorNodeLogicalBoolean
import org.squeryl.dsl.ast.LogicalBoolean

import play.api.libs.json.Json

import collins.models.asset.AssetView
import collins.models.shared.AddressPool
import collins.models.shared.IpAddressStorage
import collins.models.shared.IpAddressable
import collins.models.shared.Page
import collins.models.shared.PageParams
import collins.util.CryptoCodec
import collins.util.IpAddress
import collins.util.config.IpmiConfig

import conversions.IpmiFormat

case class IpmiInfo(
  asset_id: Long,
  username: String,
  password: String,
  gateway: Long,
  address: Long,
  netmask: Long,
  id: Long = 0) extends IpAddressable
{
  import conversions._
  override def validate() {
    super.validate()
    List(username, password).foreach { s =>
      require(s != null && s.length > 0, "Username and Password must not be empty")
    }
  }

  def toJsValue() = Json.toJson(this)
  override def asJson: String = toJsValue.toString

  def decryptedPassword(): String = IpmiInfo.decrypt(password)
  def withExposedCredentials(exposeCredentials: Boolean = false) = {
    if (exposeCredentials) {
      this.copy(password = decryptedPassword())
    } else {
      this.copy(username = "********", password = "********")
    }
  }
}

object IpmiInfo extends IpAddressStorage[IpmiInfo] {
  import org.squeryl.PrimitiveTypeMode._

  val tableDef = table[IpmiInfo]("ipmi_info")
  on(tableDef)(i => declare(
    i.id is(autoIncremented,primaryKey),
    i.asset_id is(unique),
    i.address is(unique),
    i.gateway is(indexed),
    i.netmask is(indexed)
  ))

  def createForAsset(asset: Asset): IpmiInfo = inTransaction {
    val assetId = asset.getId
    val username = getUsername(asset)
    val password = generateEncryptedPassword()
    createWithRetry(10) { attempt =>
      val (gateway, address, netmask) = getNextAvailableAddress()(None)
      val ipmiInfo = IpmiInfo(
        assetId, username, password, gateway, address, netmask
      )
      tableDef.insert(ipmiInfo)
    }
  }

  def encryptPassword(pass: String): String = {
    CryptoCodec.withKeyFromFramework.Encode(pass)
  }

  type IpmiQuerySeq = Seq[Tuple2[IpmiInfo.Enum, String]]
  def findAssetsByIpmi(page: PageParams, ipmi: IpmiQuerySeq, finder: AssetFinder): Page[AssetView] = {
    def whereClause(assetRow: Asset, ipmiRow: IpmiInfo) = {
      where(
        assetRow.id === ipmiRow.asset_id and
        finder.asLogicalBoolean(assetRow) and
        collectParams(ipmi, ipmiRow)
      )
    }
    inTransaction { log {
      val results = from(Asset.tableDef, tableDef)((assetRow, ipmiRow) =>
        whereClause(assetRow, ipmiRow)
        select(assetRow)
      ).page(page.offset, page.size).toList
      val totalCount = from(Asset.tableDef, tableDef)((assetRow, ipmiRow) =>
        whereClause(assetRow, ipmiRow)
        compute(count)
      )
      Page(results, page.page, page.offset, totalCount)
    }}
  }

  override def get(i: IpmiInfo) = getOrElseUpdate(getKey.format(i.id)) {
    tableDef.lookup(i.id).get
  }

  type Enum = Enum.Value
  object Enum extends Enumeration(1) {
    val IpmiAddress = Value("IPMI_ADDRESS")
    val IpmiUsername = Value("IPMI_USERNAME")
    val IpmiPassword = Value("IPMI_PASSWORD")
    val IpmiGateway = Value("IPMI_GATEWAY")
    val IpmiNetmask = Value("IPMI_NETMASK")
  }

  protected def decrypt(password: String) = {
    logger.debug("Decrypting %s".format(password))
    CryptoCodec.withKeyFromFramework.Decode(password).getOrElse("")
  }

  protected def getPasswordLength(): Int = IpmiConfig.passwordLength

  protected def generateEncryptedPassword(): String = {
    val length = getPasswordLength()
    CryptoCodec.withKeyFromFramework.Encode(CryptoCodec.randomString(length))
  }

  protected def getUsername(asset: Asset): String = {
    IpmiConfig.genUsername(asset)
  }

  override protected def getConfig()(implicit scope: Option[String]): Option[AddressPool] = {
    IpmiConfig.get.flatMap(_.defaultPool)
  }

  // Converts our query parameters to fragments and parameters for a query
  private[this] def collectParams(ipmi: Seq[Tuple2[IpmiInfo.Enum, String]], ipmiRow: IpmiInfo): LogicalBoolean = {
    import Enum._
    val results: Seq[LogicalBoolean] = ipmi.map { case(enum, value) =>
      enum match {
        case IpmiAddress =>
          (ipmiRow.address === IpAddress.toLong(value))
        case IpmiUsername =>
          (ipmiRow.username === value)
        case IpmiGateway =>
          (ipmiRow.gateway === IpAddress.toLong(value))
        case IpmiNetmask =>
          (ipmiRow.netmask === IpAddress.toLong(value))
        case e =>
          throw new Exception("Unhandled IPMI tag: %s".format(e))
      }
    }
    results.reduceRight((a,b) => new BinaryOperatorNodeLogicalBoolean(a, b, "and"))
  }

}
