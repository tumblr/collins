package models

import java.util.Date

import play.api.db._
import play.api.Play.current

import anorm._
import anorm.SqlParser._

case class Status(id: Pk[Long], name: String, description: String)
case class AssetType(id: Pk[Long], name: String)
case class Asset(
  id: Pk[Long],
  secondary_id: String,
  status: Int,
  assetType: Int,
  created: Option[Date], updated: Option[Date], deleted: Option[Date])

case class AssetMeta(id: Pk[Long], priority: Int, name: String, description: String)
object AssetMeta {
  val simple = {
    get[Pk[Long]]("asset_meta.id") ~/
    get[Int]("asset_meta.priority") ~/
    get[String]("asset_meta.name") ~/
    get[String]("asset_meta.description") ^^ {
      case id~priority~name~description => AssetMeta(id, priority, name, description)
    }
  }

  def getAll(): Seq[AssetMeta] = {
    DB.withConnection("collins") { implicit connection =>
      SQL("select * from asset_meta where priority > -1 order by priority asc").as(AssetMeta.simple *)
    }
  }
}

case class AssetMetaValue(asset_id: Long, meta_id: Long, value: String)
