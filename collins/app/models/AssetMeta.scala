package models

import anorm._
import anorm.SqlParser._
import play.api.cache.Cache
import play.api.Play.current

case class AssetMeta(
    pk: Pk[Long],
    name: String,
    priority: Int,
    label: String,
    description: String)
  extends BasicModel[Long]
object AssetMeta extends BasicQueries[AssetMeta,Long] {
  val tableName = "asset_meta"
  val simple = {
    get[Pk[Long]]("asset_meta.id") ~/
    get[String]("asset_meta.name") ~/
    get[Int]("asset_meta.priority") ~/
    get[String]("asset_meta.label") ~/
    get[String]("asset_meta.description") ^^ {
      case id~name~priority~label~description => AssetMeta(id, name, priority, label, description)
    }
  }

  def getViewable(): List[AssetMeta] = {
    // change to use stuff in Enum
    PlayDB.withConnection(db) { implicit connection =>
      Cache.get[List[AssetMeta]]("AssetMeta.getViewable").getOrElse {
        logger.debug("Cache miss for AssetMeta.getViewable")
        val res = SQL("select * from asset_meta where priority > -1 order by priority asc").as(AssetMeta.simple *)
        Cache.set("AssetMeta.getViewable", res)
        res
      }
    }
  }

  type Enum = Enum.Value
  object Enum extends Enumeration(1) {
    val ServiceTag = Value("SERVICE_TAG")
    val ChassisTag = Value("CHASSIS_TAG")
    val IpAddress = Value("IP_ADDRESS")
    val IpmiAddress = Value("IPMI_ADDRESS")
    val Hostname = Value("HOSTNAME")
    val MacAddress = Value("MAC_ADDRESS")
    val RackPosition = Value("RACK_POSITION")
    val PowerPort = Value("POWER_PORT")
    val SwitchPort = Value("SWITCH_PORT")
    val IpmiCredentials = Value("IPMI_CREDENTIALS")
  }
}


