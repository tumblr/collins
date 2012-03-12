package models

import util.{Cache, CryptoAccessor, CryptoCodec, Helpers, IpAddress}
import org.squeryl.PrimitiveTypeMode._
import org.squeryl.{Query, Schema, Table}

import play.api._
import play.api.libs.json._

import java.sql.Connection

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
  
  val tableDef = table[IpmiInfo]("ipmi_info")
  private[this] val logger = Logger.logger

  val DefaultPasswordLength = 12
  val RandomUsername = false

  override def cacheKeys(a: IpmiInfo) = Seq(
    "IpmiInfo.findByAsset(%d)".format(a.asset_id)
  )

  override def delete(a: IpmiInfo): Int = withConnection {
    tableDef.deleteWhere(i => i.id === a.id)
  }

  def deleteByAsset(a: Asset): Int = withConnection {
    tableDef.deleteWhere(i => i.asset_id === a.getId)
  }

  def findByAsset(asset: Asset): Option[IpmiInfo] = {
    Cache.getOrElseUpdate("IpmiInfo.findByAsset(%d)".format(asset.getId)) {
      withConnection {
        tableDef.where(a => a.asset_id === asset.getId).headOption
      }
    }
  }

  import java.sql.Timestamp
  import conversions._
  import org.squeryl.Query
  import org.squeryl.dsl.ast.{BinaryOperatorNodeLogicalBoolean, LogicalBoolean}
  def aFinderToQuery(finder: AssetFinder, a: MockAsset): LogicalBoolean = {
    val statusId: Option[Int] = finder.status.map(_.id)
    val typeId: Option[Int] = finder.assetType.map(_.id)
    val createdAfter: Option[Timestamp] = finder.createdAfter.map(_.asTimestamp)
    val createdBefore: Option[Timestamp] = finder.createdBefore.map(_.asTimestamp)
    val updatedAfter: Option[Timestamp] = finder.updatedAfter.map(_.asTimestamp)
    val updatedBefore: Option[Timestamp] = finder.updatedBefore.map(_.asTimestamp)
    val ops = List[LogicalBoolean](
      (a.tag === finder.tag.?) and
      (a.status === statusId.?) and
      (a.created gte createdAfter.?) and
      (a.created lte createdBefore.?) and
      (a.updated gte updatedAfter.?) and
      (a.updated lte updatedBefore.?)
    )
    ops.reduceRight((a,b) => new BinaryOperatorNodeLogicalBoolean(a, b, "and"))
  }

  type IpmiQuerySeq = Seq[Tuple2[IpmiInfo.Enum, String]]
  import anorm.Id
  def findAssetsByIpmi(page: PageParams, ipmi: IpmiQuerySeq, finder: AssetFinder): Page[Asset] = {
    def whereClause(assetRow: MockAsset, ipmiRow: IpmiInfo) = {
      where(
        assetRow.id === ipmiRow.asset_id and
        aFinderToQuery(finder, assetRow) and
        collectParams(ipmi, ipmiRow)
      )
    }
    withConnection {
      val results = from(MockAsset.tableDef, tableDef)((assetRow, ipmiRow) =>
        whereClause(assetRow, ipmiRow)
        select(assetRow)
      ).page(page.offset, page.size).toList
      val totalCount = from(MockAsset.tableDef, tableDef)((assetRow, ipmiRow) =>
        whereClause(assetRow, ipmiRow)
        compute(count)
      )
      val pageResults = results.map { asset =>
        Asset(Id(asset.id), asset.tag, asset.status, asset.asset_type, asset.created, asset.updated, asset.deleted)
      }
      Page(pageResults, page.page, page.offset, totalCount)
    }
  }

  def createForAsset(asset: Asset): IpmiInfo = {
    val assetId = asset.getId
    val (gateway, address, netmask) = getAddress()
    val username = getUsername(asset)
    val password = generateEncryptedPassword()
    val ipmiInfo = IpmiInfo(
      assetId, username, password, gateway, address, netmask
    )
    tableDef.insert(ipmiInfo) // FIXME this will be broken if called outside of a transaction
  }

  type Enum = Enum.Value
  object Enum extends Enumeration(1) {
    val IpmiAddress = Value("IPMI_ADDRESS")
    val IpmiUsername = Value("IPMI_USERNAME")
    val IpmiPassword = Value("IPMI_PASSWORD")
    val IpmiGateway = Value("IPMI_GATEWAY")
    val IpmiNetmask = Value("IPMI_NETMASK")
  }

  protected def getAddress(): Tuple3[Long,Long,Long] = {
    val gateway: Long = getGateway()
    val netmask: Long = getNetmask()
    val address: Long = getNextAvailableAddress(netmask)
    (gateway, address, netmask)
  }

  protected def getNextAvailableAddress(netmask: Long): Long = {
    val currentMax: Long = withConnection {
      from(tableDef)(t => compute(nvl(max(t.address), 2)))
    }
    IpAddress.nextAvailableAddress(currentMax, netmask)
  }

  protected def getGateway(): Long = {
    getAddressFromConfig("gateway")
  }
  protected def getNetmask(): Long = {
    getAddressFromConfig("netmask")
  }

  protected def getAddressFromConfig(key: String): Long = {
    getConfig() match {
      case None => throw new RuntimeException("no ipmi configuration found")
      case Some(config) => config.getString(key) match {
        case Some(value) => IpAddress.toLong(value)
        case None => throw new RuntimeException("no %s key found in configuration".format(key))
      }
    }
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

  def encryptPassword(pass: String): String = {
    CryptoCodec(getCryptoKeyFromFramework()).Encode(pass)
  }

  protected def generateEncryptedPassword(): String = {
    val length = getPasswordLength()
    CryptoCodec(getCryptoKeyFromFramework()).Encode(CryptoCodec.randomString(length))
  }

  protected def getUsername(asset: Asset): String = {
    val randomUsername = getConfig() match {
      case None => RandomUsername
      case Some(config) => config.getBoolean("randomUsername") match {
        case Some(bool) => bool
        case None => RandomUsername
      }
    }
    randomUsername match {
      case true => CryptoCodec.randomString(8)
      case false => asset.tag + "-ipmi"
    }
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
