package models

import util.{CryptoAccessor, CryptoCodec, IpAddress}

import anorm._
import anorm.defaults._
import anorm.SqlParser._
import play.api._

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
  def toMap(): Map[String,String] = Map(
    IpmiAddress.toString -> dottedAddress,
    IpmiGateway.toString -> dottedGateway,
    IpmiNetmask.toString -> dottedNetmask,
    IpmiUsername.toString -> username,
    IpmiPassword.toString -> decryptedPassword
  )
}

object IpmiInfo extends Magic[IpmiInfo](Some("ipmi_info")) with Dao[IpmiInfo] {
  val DefaultPasswordLength = 12
  val RandomUsername = false

  def apply(asset: Asset)(implicit con: Connection) = {
    if (asset.status == Status.Enum.Incomplete.id) {
      createNewIpmiInfo(asset)
    } else {
      findByAsset(asset).get
    }
  }

  def findByAsset(asset: Asset): Option[IpmiInfo] = {
    Model.withConnection { implicit con =>
      IpmiInfo.find("asset_id={asset_id}").on('asset_id -> asset.getId).singleOption()
    }
  }

  protected def createNewIpmiInfo(asset: Asset)(implicit con: Connection): IpmiInfo = {
    val assetId = asset.getId
    val (gateway, address, netmask) = getAddress()
    val username = getUsername(asset)
    val password = generateEncryptedPassword()
    val ipmiInfo = IpmiInfo(
      NotAssigned, Id(assetId), username, password, gateway, address, netmask
    )
    IpmiInfo.create(ipmiInfo)
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

  protected def generateEncryptedPassword(): String = {
    val length = getConfig() match {
      case None => DefaultPasswordLength
      case Some(config) => config.getInt("passwordLength") match {
        case None => DefaultPasswordLength
        case Some(len) if len > 0 && len <= 24 => len
        case _ => throw new IllegalArgumentException("passwordLength must be between 1 and 24")
      }
    }
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

  type Enum = Enum.Value
  object Enum extends Enumeration(1) {
    val IpmiAddress = Value("IPMI_ADDRESS")
    val IpmiUsername = Value("IPMI_USERNAME")
    val IpmiPassword = Value("IPMI_PASSWORD")
    val IpmiGateway = Value("IPMI_GATEWAY")
    val IpmiNetmask = Value("IPMI_NETMASK")
  }
}
