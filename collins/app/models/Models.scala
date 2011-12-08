package models

import java.util.Date

import play.api.cache.Cache
import play.api.db._
import play.api.Play.current

import anorm._
import anorm.SqlParser._

case class Status(id: Pk[Int], name: String, description: String)
object Status extends BasicQueries[Status,Int] {
  val tableName = "status"
  val simple = {
    get[Pk[Int]]("status.id") ~/
    get[String]("status.name") ~/
    get[String]("status.description") ^^ {
      case id~name~description => Status(id, name, description)
    }
  }

  type Enum = Enum.Value
  object Enum extends Enumeration(1) {
    val New, Unallocated, Allocated, Cancelled, Maintenance, Decommissioned, Incomplete = Value
  }

}

case class AssetType(id: Pk[Int], name: String)
object AssetType extends BasicQueries[AssetType,Int] {
  val tableName = "asset_type"
  val simple = {
    get[Pk[Int]]("asset_type.id") ~/
    get[String]("asset_type.name") ^^ {
      case id~name => AssetType(id, name)
    }
  }

  type Enum = Enum.Value
  object Enum extends Enumeration(1) {
    val ServerNode = Value("SERVER_NODE")
    val ServerChassis = Value("SERVER_CHASSIS")
    val Rack = Value("RACK")
    val Switch = Value("SWITCH")
    val Router = Value("ROUTER")
    val PowerCircuit = Value("POWER_CIRCUIT")
    val PowerStrip = Value("POWER_STRIP")
  }
}

case class Asset(
  id: Pk[Long],
  secondaryId: String,
  status: Int,
  assetType: Int,
  created: Date, updated: Option[Date], deleted: Option[Date])
{
  def isNew(): Boolean = {
    status == Status.Enum.New.id
  }
  def getStatus(): Status = {
    Status.findById(status).get
  }
  def getType(): AssetType = {
    AssetType.findById(assetType).get
  }
  def getAttributes(specs: Set[AssetMeta.Enum] = Set.empty): List[MetaWrapper] = {
    specs.isEmpty match {
      case true =>
        AssetMetaValue.findAllByAssetId(id.get).toList
      case false =>
        AssetMetaValue.findSomeByAssetId(specs, id.get).toList
    }
  }
}

object Asset extends BasicQueries[Asset,Long] {
  val tableName = "asset"
  val simple = {
    get[Pk[Long]]("asset.id") ~/
    get[String]("asset.secondary_id") ~/
    get[Int]("asset.status") ~/
    get[Int]("asset.asset_type") ~/
    get[Date]("asset.created") ~/
    get[Option[Date]]("asset.updated") ~/
    get[Option[Date]]("asset.deleted") ^^ {
      case id~secondary_id~status~asset_type~created~updated~deleted =>
        Asset(id, secondary_id, status, asset_type, created, updated, deleted)
    }
  }

  def findBySecondaryId(id: String): Option[Asset] = {
    val query = "select * from asset where secondary_id = {id}"
    DB.withConnection(db) { implicit connection =>
      SQL(query).on('id -> id).as(Asset.simple ?)
    }
  }

  def findByMeta(list: Seq[(AssetMeta.Enum,String)]): Seq[Asset] = {
    val query = "select distinct asset_id from asset_meta_value where "
    var count = 0
    val params = list.map { case(k,v) =>
      val id: String = k.toString + "_" + count
      count += 1
      val fragment = "asset_meta_value.asset_meta_id = %d and asset_meta_value.value like {%s}".format(k.id, id)
      (fragment, (Symbol(id), v))
    }
    val nq = query + params.map { _._1 }.mkString(" and ")
    DB.withConnection(db) { implicit connection =>
      val ids = SQL(
        nq
      ).on(
        params.map { case(s, (symbol, value)) =>
          (symbol, toParameterValue(value))
        }:_*
      ).as(scalar[Long] *)
      ids.isEmpty match {
        case true => Seq.empty
        case false => Asset.findByIds(ids)
      }
    }
  }

}

case class AssetMeta(id: Pk[Long], name: String, priority: Int, label: String, description: String)
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
    DB.withConnection(db) { implicit connection =>
      Cache.get[List[AssetMeta]]("AssetMeta.getViewable").getOrElse {
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
case class MetaWrapper(_meta: AssetMeta, _value: AssetMetaValue) {
  def getAssetId(): Long = _value.asset_id
  def getMetaId(): Long = _meta.id.get
  def getId(): (Long,Long) = (getAssetId(), getMetaId())
  def getName(): String = _meta.name
  def getNameEnum(): Option[AssetMeta.Enum] = try {
    Some(AssetMeta.Enum.withName(getName()))
  } catch { case _ => None }
  def getPriority(): Int = _meta.priority
  def getLabel(): String = _meta.label
  def getDescription(): String = _meta.description
  def getValue(): String = _value.value
}
case class AssetMetaValue(asset_id: Long, meta_id: Long, value: String) {
  def getAsset(): Asset = {
    Asset.findById(asset_id).get
  }
  def getMeta(): AssetMeta = {
    AssetMeta.findById(meta_id).get
  }
}
object AssetMetaValue {
  val tableName = "asset_meta_value"
  val simple = {
    get[Long]("asset_meta_value.asset_id") ~/
    get[Long]("asset_meta_value.asset_meta_id") ~/
    get[String]("asset_meta_value.value") ^^ {
      case asset_id~asset_meta_id~meta_value => AssetMetaValue(asset_id, asset_meta_id, meta_value)
    }
  }

  val withAssetMeta = AssetMetaValue.simple ~/ AssetMeta.simple ^^ {
    case assetMetaValue~assetMeta => MetaWrapper(assetMeta, assetMetaValue)
  }

  def findSomeByAssetId(spec: Set[AssetMeta.Enum], id: Long): Seq[MetaWrapper] = {
    Nil
  }
  def findAllByAssetId(id: Long): Seq[MetaWrapper] = {
    val query = """
    select
      *
    from
      asset_meta, asset_meta_value
    where
      asset_meta_value.asset_meta_id = asset_meta.id
        and
      asset_meta_value.asset_id = {id}
    """
    DB.withConnection("collins") { implicit connection =>
      SQL(query).on('id -> id).as(AssetMetaValue.withAssetMeta *)
    }
  }
}

trait BasicQueries[T,PK] { self =>
  protected val db: String = "collins"
  val tableName: String
  val simple: Parser[T]

  def findById(id: PK): Option[T] = {
    val query = "select * from %s where id = {id}".format(tableName)
    findById(id, query)
  }
  def findByIds(ids: Seq[PK]): Seq[T] = {
    val query = "select * from %s where id in (%s)".format(tableName, ids.mkString(","))
    DB.withConnection(db) { implicit connection =>
      SQL(query).as(simple *)
    }
  }

  protected def nextId(seq: String): Long = {
    val query = "select next value for %s".format(seq)
    DB.withConnection(db) { implicit connection =>
      SQL(query).as(scalar[Long])
    }
  }
  protected def findById(id: PK, query: String): Option[T] = {
    DB.withConnection(db) { implicit connection =>
      SQL(query).on('id -> id).as(simple ?)
    }
  }
}

