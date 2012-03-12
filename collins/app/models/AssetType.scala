package models

import util.Cache

import org.squeryl.PrimitiveTypeMode._
import org.squeryl.{Schema, Table}

case class AssetType(name: String, id: Int = 0) extends ValidatedEntity[Int] {
  def getId(): Int = id
  override def validate() {
    require(name != null && name.length > 0, "Name must not be empty")
  }
}

object AssetType extends Schema with AnormAdapter[AssetType] {

  val tableDef = table[AssetType]("asset_type")
  on(tableDef)(a => declare(
    a.id is(autoIncremented,primaryKey),
    a.name is(unique)
  ))

  override def cacheKeys(a: AssetType) = Seq(
    "AssetType.findById(%d)".format(a.id),
    "AssetType.findByName(%s)".format(a.name.toUpperCase)
  )

  def findById(id: Int): Option[AssetType] =
    Cache.getOrElseUpdate("AssetType.findById(%d)".format(id)) {
      withConnection {
        tableDef.lookup(id)
      }
    }

  def findByName(name: String): Option[AssetType] =
    Cache.getOrElseUpdate("AssetType.findByName(%s)".format(name.toUpperCase)) {
      withConnection {
        tableDef.where(a =>
          a.name.toLowerCase === name.toLowerCase
        ).headOption
      }
    }

  def fromEnum(enum: AssetType.Enum): AssetType =
    new AssetType(enum.toString, enum.id)

  def fromString(name: String): Option[AssetType] = {
    try {
      Some(fromEnum(Enum.withName(name)))
    } catch {
      case e => findByName(name)
    }
  }

  override def delete(a: AssetType): Int = withConnection {
    tableDef.deleteWhere(p => p.id === a.id)
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
    val DataCenter = Value("DATA_CENTER")
    val Config = Value("CONFIGURATION")
  }
}
