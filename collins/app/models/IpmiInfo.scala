package models

import util.{Cache, CryptoAccessor, CryptoCodec, Helpers, IpAddress, IpAddressCalc}
import org.squeryl.PrimitiveTypeMode._
import org.squeryl.Schema
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
  id: Long = 0) extends ValidatedEntity[Long]
{
  import IpmiInfo.Enum._

  override def validate() {
    List(gateway, address, netmask).foreach { i =>
      require(i > 0, "IP gateway, address and netmask must be positive")
    }
    List(username, password).foreach { s =>
      require(s != null && s.length > 0, "Username and Password must not be empty")
    }
  }
  override def asJson: String = {
    Json.stringify(JsObject(forJsonObject))
  }

  def dottedAddress(): String = IpAddress.toString(address)
  def dottedGateway(): String = IpAddress.toString(gateway)
  def dottedNetmask(): String = IpAddress.toString(netmask)
  def decryptedPassword(): String = IpmiInfo.decrypt(password)
  def getId(): Long = id
  def getAssetId(): Long = asset_id

  def withExposedCredentials(exposeCredentials: Boolean = false) = {
    if (exposeCredentials) {
      this.copy(password = decryptedPassword())
    } else {
      this.copy(username = "********", password = "********")
    }
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

object IpmiInfo extends Schema with AnormAdapter[IpmiInfo] {
  private[this] val logger = Logger.logger
  val DefaultPasswordLength = 12

  val tableDef = table[IpmiInfo]("ipmi_info")
  on(tableDef)(i => declare(
    i.id is(autoIncremented,primaryKey),
    i.asset_id is(unique),
    i.address is(unique),
    i.gateway is(indexed),
    i.netmask is(indexed)
  ))

  override def cacheKeys(a: IpmiInfo) = Seq(
    "IpmiInfo.findByAsset(%d)".format(a.asset_id)
  )

  def createForAsset(asset: Asset): IpmiInfo = inTransaction {
    val assetId = asset.getId
    val (gateway, address, netmask) = getNextAvailableAddress()
    val username = getUsername(asset)
    val password = generateEncryptedPassword()
    val ipmiInfo = IpmiInfo(
      assetId, username, password, gateway, address, netmask
    )
    tableDef.insert(ipmiInfo)
  }

  override def delete(a: IpmiInfo): Int = inTransaction {
    afterDeleteCallback(a) {
      tableDef.deleteWhere(i => i.id === a.id)
    }
  }

  def deleteByAsset(a: Asset): Int = inTransaction {
    findByAsset(a).map { ipmi =>
      delete(ipmi)
    }.getOrElse(0)
  }

  def encryptPassword(pass: String): String = {
    CryptoCodec(getCryptoKeyFromFramework()).Encode(pass)
  }

  type IpmiQuerySeq = Seq[Tuple2[IpmiInfo.Enum, String]]
  def findAssetsByIpmi(page: PageParams, ipmi: IpmiQuerySeq, finder: AssetFinder): Page[Asset] = {
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

  def findByAsset(asset: Asset): Option[IpmiInfo] = {
    getOrElseUpdate("IpmiInfo.findByAsset(%d)".format(asset.getId)) {
      tableDef.where(a => a.asset_id === asset.getId).headOption
    }
  }

  override def get(i: IpmiInfo) = getOrElseUpdate("IpmiInfo.get(%d)".format(i.id)) {
    tableDef.lookup(i.id).get
  }

  def getNextAvailableAddress(overrideStart: Option[String] = None): Tuple3[Long,Long,Long] = {
    val network = getNetwork
    val startAt = overrideStart.orElse(getStartAddress)
    val calc = IpAddressCalc(network, startAt)
    val gateway: Long = getGateway().getOrElse(calc.minAddressAsLong)
    val netmask: Long = calc.netmaskAsLong
    val currentMax: Option[Long] = getCurrentMaxAddress(
      calc.minAddressAsLong, calc.maxAddressAsLong
    )
    val address: Long = calc.nextAvailableAsLong(currentMax)
    (gateway, address, netmask)
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

  protected def getCurrentMaxAddress(minAddress: Long, maxAddress: Long): Option[Long] = inTransaction {
    from(tableDef)(t =>
      where(
        (t.address gte minAddress) and
        (t.address lte maxAddress)
      )
      compute(max(t.address))
    )
  }

  protected def getGateway(): Option[Long] = getConfig() match {
    case None => None
    case Some(config) => config.getString("gateway") match {
      case Some(value) => Option(IpAddress.toLong(value))
      case None => None
    }
  }
  protected def getNetwork(): String = getConfig() match {
    case None => throw new RuntimeException("no ipmi configuration found")
    case Some(config) => config.getString("network") match {
      case Some(value) => value
      case None => throw new RuntimeException("ipmi.network not specified")
    }
  }
  protected def getStartAddress(): Option[String] = getConfig() match {
    case None => None
    case Some(c) => c.getString("startAddress")
  }

  protected def decrypt(password: String) = {
    logger.debug("Decrypting %s".format(password))
    CryptoCodec(getCryptoKeyFromFramework()).Decode(password).getOrElse("")
  }

  protected def getCryptoKeyFromFramework(): String = {
    Play.maybeApplication.map { app =>
      app.global match {
        case c: CryptoAccessor => c.getCryptoKey()
        case _ => throw new RuntimeException("Application is not a CryptoAccessor")
      }
    }.getOrElse(throw new RuntimeException("Not in application context"))
  }

  protected def getPasswordLength(): Int = {
    getConfig() match {
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
    CryptoCodec(getCryptoKeyFromFramework()).Encode(CryptoCodec.randomString(length))
  }

  protected def getUsername(asset: Asset): String = {
    Username(asset, getConfig, false).get
  }

  protected def getConfig(): Option[Configuration] = {
    Helpers.getConfig("ipmi")
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
