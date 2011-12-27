package models

import Model.defaults._
import util.{CryptoAccessor, CryptoCodec, IpAddress}

import anorm._
import anorm.SqlParser._
import play.api._
import play.api.libs.json._

import java.sql.Connection

case class IpmiInfo(
  id: Pk[java.lang.Long],
  asset_id: Id[java.lang.Long],
  username: String,
  password: String,
  gateway: Long,
  address: Long,
  netmask: Long)
{
  import IpmiInfo.Enum._
  def dottedAddress(): String = IpAddress.toString(address)
  def dottedGateway(): String = IpAddress.toString(gateway)
  def dottedNetmask(): String = IpAddress.toString(netmask)
  def decryptedPassword(): String = IpmiInfo.decrypt(password)
  def getId(): Long = id.get
  def getAssetId(): Long = asset_id.get

  def toMap(secure: Boolean = true): Map[String,String] = strip(secure, Map(
    "ID" -> getId().toString,
    "ASSET_ID" -> asset_id.get.toString,
    IpmiAddress.toString -> dottedAddress,
    IpmiGateway.toString -> dottedGateway,
    IpmiNetmask.toString -> dottedNetmask,
    IpmiUsername.toString -> username,
    IpmiPassword.toString -> decryptedPassword
  ))
  def toJsonMap(secure: Boolean = true): Map[String,JsValue] = strip(secure, Map(
    "ID" -> JsNumber(getId()),
    "ASSET_ID" -> JsNumber(getAssetId()),
    IpmiAddress.toString -> JsString(dottedAddress),
    IpmiGateway.toString -> JsString(dottedGateway),
    IpmiNetmask.toString -> JsString(dottedNetmask),
    IpmiUsername.toString -> JsString(username),
    IpmiPassword.toString -> JsString(decryptedPassword)
  ))

  private val sensitive = Set(IpmiUsername.toString, IpmiPassword.toString)
  private def strip[V](secure: Boolean, map: Map[String, V]): Map[String, V] = {
    secure match {
      case true => map.filterKeys(!sensitive.contains(_))
      case false => map
    }
  }
}

object IpmiInfo extends Magic[IpmiInfo](Some("ipmi_info")) {
  private[this] val logger = Logger.logger

  val DefaultPasswordLength = 12
  val RandomUsername = false

  def findByAsset(asset: Asset): Option[IpmiInfo] = {
    Model.withConnection { implicit con =>
      IpmiInfo.find("asset_id={asset_id}").on('asset_id -> asset.getId).singleOption()
    }
  }

  type IpmiQuerySeq = Seq[Tuple2[IpmiInfo.Enum, String]]
  def findAssetsByIpmi(page: PageParams, ipmi: IpmiQuerySeq, finder: AssetFinder): Page[Asset] = {
    val queryPlaceholder = """
      select %s from asset
      join ipmi_info ipmi on asset.id = ipmi.asset_id
      where %s
    """
    val finderQuery = DaoSupport.flattenSql(Seq(finder.asQueryFragment(), collectParams(ipmi)))
    val finderQueryFrag = finderQuery.sql.query
    val query = queryPlaceholder.format("*", finderQueryFrag) + " limit {pageSize} offset {offset}"
    val countQuery = queryPlaceholder.format("count(*)", finderQueryFrag)
    val paramsNoPaging = finderQuery.params
    val paramsWithPaging = Seq(
      ('pageSize -> toParameterValue(page.size)),
      ('offset -> toParameterValue(page.offset))
    ) ++ paramsNoPaging
    Model.withConnection { implicit con =>
      val assets = SQL(query).on(paramsWithPaging:_*).as(Asset *)
      val count = SQL(countQuery).on(paramsNoPaging:_*).as(scalar[Long])
      Page(assets, page.page, page.offset, count)
    }
  }

  def createForAsset(asset: Asset)(implicit con: Connection): IpmiInfo = {
    val assetId = asset.getId
    val (gateway, address, netmask) = getAddress()
    val username = getUsername(asset)
    val password = generateEncryptedPassword()
    val ipmiInfo = IpmiInfo(
      NotAssigned, Id(assetId), username, password, gateway, address, netmask
    )
    IpmiInfo.create(ipmiInfo)
  }

  type Enum = Enum.Value
  object Enum extends Enumeration(1) {
    val IpmiAddress = Value("IPMI_ADDRESS")
    val IpmiUsername = Value("IPMI_USERNAME")
    val IpmiPassword = Value("IPMI_PASSWORD")
    val IpmiGateway = Value("IPMI_GATEWAY")
    val IpmiNetmask = Value("IPMI_NETMASK")
  }

  protected def getAddress()(implicit con: Connection): Tuple3[Long,Long,Long] = {
    val gateway: Long = getGateway()
    val netmask: Long = getNetmask()
    val address: Long = getNextAvailableAddress(netmask)
    (gateway, address, netmask)
  }

  protected def getNextAvailableAddress(netmask: Long)(implicit con: Connection): Long = {
    val currentMax = IpmiInfo.find("select max(address) as address from ipmi_info").as(scalar[Long])
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
        case Some(len) if len > 0 && len <= 24 => len
        case _ => throw new IllegalArgumentException("passwordLength must be between 1 and 24")
      }
    }
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
    Play.maybeApplication.map { app =>
      app.configuration.getSub("ipmi")
    }.getOrElse(None)
  }

  // Converts our query parameters to fragments and parameters for a query
  private[this] def collectParams(ipmi: Seq[Tuple2[IpmiInfo.Enum, String]]): SimpleSql[Row] = {
    import Enum._
    val results = ipmi.zipWithIndex.map { case(tuple, size) =>
      val enum = tuple._1
      val value = tuple._2
      enum match {
        case IpmiAddress =>
          val sub = "address_%d".format(size)
          SqlQuery("address={%s}".format(sub)).on(sub -> IpAddress.toLong(value))
        case IpmiUsername =>
          val sub = "username_%d".format(size)
          SqlQuery("username={%s}".format(sub)).on(sub -> value)
        case IpmiGateway =>
          val sub = "gateway_%d".format(size)
          SqlQuery("gateway={%s}".format(sub)).on(sub -> IpAddress.toLong(value))
        case IpmiNetmask =>
          val sub = "netmask_%d".format(size)
          SqlQuery("netmask={%s}".format(sub)).on(sub -> IpAddress.toLong(value))
        case e =>
          throw new Exception("Unhandled IPMI tag: %s".format(e))
      }
    }
    DaoSupport.flattenSql(results)
  }


}
