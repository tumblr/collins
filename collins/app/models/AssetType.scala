package models

import anorm._
import anorm.SqlParser._
import java.sql._

case class AssetType(pk: Pk[Int], name: String) extends BasicModel[Int]
object AssetType extends BasicQueries[AssetType,Int] {
  val tableName = "asset_type"
  val simple = {
    get[Pk[Int]]("asset_type.id") ~/
    get[String]("asset_type.name") ^^ {
      case id~name => AssetType(id, name)
    }
  }

  def batchCreate(atypes: Seq[AssetType])(implicit con: Connection): Int = {
    atypes.foldLeft(0) { case(count, atype) =>
      if (atype.id > 0) throw new IllegalArgumentException("Can only create for asset type with id 0")
      count + SQL(
        """
          insert into asset_type values (
            id = (select next value for asset_type_seq),
            name = {name}
          )
        """
      ).on(
        'name -> atype.name
      ).executeUpdate()
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
