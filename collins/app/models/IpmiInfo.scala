package models

import util._
import org.squeryl.dsl.ast.{BinaryOperatorNodeLogicalBoolean, LogicalBoolean}

import play.api._
import play.api.libs.json._

case class IpmiInfo(
  asset_id: Long,
  username: String,
  password: String,
  gateway: Long,
  address: Long,
  netmask: Long,
  id: Long = 0) extends IpAddressable
{
  import IpmiInfo.Enum._

  override def validate() {
    super.validate()
    List(username, password).foreach { s =>
      require(s != null && s.length > 0, "Username and Password must not be empty")
    }
  }

  override def asJson: String = {
    Json.stringify(JsObject(forJsonObject))
  }

  def decryptedPassword(): String = IpmiInfo.decrypt(password)
  def withExposedCredentials(exposeCredentials: Boolean = false) = {
    if (exposeCredentials) {
      this.copy(password = decryptedPassword())
    } else {
      this.copy(username = "********", password = "********")
    }
  }
  def toJsonObject(): JsObject = {
    JsObject(forJsonObject)
  }
  def forJsonObject(): Seq[(String,JsValue)] = Seq(
    "ID" -> JsNumber(getId()),
    "ASSET_ID" -> JsNumber(getAssetId()),
    IpmiAddress.toString -> JsString(dottedAddress),
    IpmiGateway.toString -> JsString(dottedGateway),
    IpmiNetmask.toString -> JsString(dottedNetmask),
    IpmiUsername.toString -> JsString(username),
    IpmiPassword.toString -> JsString(password)
  )
}

object IpmiInfo extends IpAddressStorage[IpmiInfo] {
  import org.squeryl.PrimitiveTypeMode._

  val DefaultPasswordLength = 12

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
    createWithRetry(10) {
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
    inTransaction {
      val results = from(Asset.tableDef, tableDef)((assetRow, ipmiRow) =>
        whereClause(assetRow, ipmiRow)
        select(assetRow)
      ).page(page.offset, page.size).toList
      val totalCount = from(Asset.tableDef, tableDef)((assetRow, ipmiRow) =>
        whereClause(assetRow, ipmiRow)
        compute(count)
      )
      Page(results, page.page, page.offset, totalCount)
    }
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

  case class Username(asset: Asset, config: Option[Configuration], randomUsername: Boolean = false) {
    def this(asset: Asset, config: Configuration, randomUsername: Boolean) =
      this(asset, Some(config), randomUsername)

    def isRandom: Boolean = config match {
      case None => randomUsername
      case Some(cfg) => cfg.getBoolean("randomUsername") match {
        case Some(bool) => bool
        case None => randomUsername
      }
    }

    def fromAsset: String = "%s-ipmi".format(asset.tag)

    def get(): String = {
      isRandom match {
        case true => CryptoCodec.randomString(8)
        case false => config match {
          case None => fromAsset
          case Some(cfg) => cfg.getString("username") match {
            case Some(uname) => uname
            case None => fromAsset
          }
        }
      }
    }
  }

  protected def decrypt(password: String) = {
    logger.debug("Decrypting %s".format(password))
    CryptoCodec.withKeyFromFramework.Decode(password).getOrElse("")
  }

  protected def getPasswordLength(): Int = {
    getConfig()(None) match {
      case None => DefaultPasswordLength
      case Some(config) => config.getInt("passwordLength") match {
        case None => DefaultPasswordLength
        case Some(len) if len > 0 && len <= 16 => len
        case _ => throw new IllegalArgumentException("passwordLength must be between 1 and 16")
      }
    }
  }

  protected def generateEncryptedPassword(): String = {
    val length = getPasswordLength()
    CryptoCodec.withKeyFromFramework.Encode(CryptoCodec.randomString(length))
  }

  protected def getUsername(asset: Asset): String = {
    Username(asset, getConfig()(None), false).get
  }

  override protected def getConfig()(implicit scope: Option[String]): Option[Configuration] = {
    AppConfig.ipmi
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
