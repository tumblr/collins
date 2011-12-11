package models

import anorm._
import anorm.defaults._
import anorm.SqlParser._
import java.sql._

case class AssetType(id: Pk[java.lang.Integer], name: String) {
  def getId(): Int = id.get
}

object AssetType extends Magic[AssetType](Some("asset_type")) with Dao[AssetType] {
  def create(atypes: Seq[AssetType])(implicit con: Connection): Seq[AssetType] = {
    atypes.foldLeft(List[AssetType]()) { case(list, atype) =>
      if (atype.id.isDefined) throw new IllegalArgumentException("Use update, id already defined")
      AssetType.create(atype) +: list
    }.reverse
  }

  def findById(id: Int) = Model.withConnection { implicit con =>
    AssetType.find("id={id}").on('id -> id).singleOption()
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
