package models

import java.util.Date

import play.api.db._
import play.api.Play.current

import anorm._
import anorm.SqlParser._

case class Status(id: Pk[Int], name: String, description: String)
object Status {
  val simple = {
    get[Pk[Int]]("status.id") ~/
    get[String]("status.name") ~/
    get[String]("status.description") ^^ {
      case id~name~description => Status(id, name, description)
    }
  }
}

case class AssetType(id: Pk[Int], name: String)
object AssetType {
  val simple = {
    get[Pk[Int]]("asset_type.id") ~/
    get[String]("asset_type.name") ^^ {
      case id~name => AssetType(id, name)
    }
  }
}

case class AssetMetaWrapper(meta: AssetMeta, value: AssetMetaValue)
case class ProperAsset(
  id: Long,
  secondaryId: String,
  status: Status,
  assetType: AssetType,
  created: Date, updated: Option[Date], deleted: Option[Date],
  metas: List[AssetMetaWrapper])

case class Asset(
  id: Pk[Long],
  secondaryId: String,
  status: Int,
  assetType: Int,
  created: Date, updated: Option[Date], deleted: Option[Date])
object Asset {
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

  val withAll = Asset.simple ~/ AssetMeta.simple ~/ AssetMetaValue.simple ^^ {
    case asset~meta~value => (asset,meta,value)
  }

  def findByMeta(params: Map[String, Seq[String]]) = {
    DB.withConnection("collins") { implicit connection =>
      val t = SQL(
        """
          select
            distinct asset_id
          from
            asset_meta_value
          where
            asset_meta_value.value like {mv}
              and
            asset_meta_value.asset_meta_id = {mid}
        """
      ).on(
        'mv -> "10.0.0.1",
        'mid -> 4
      ).as(scalar[Long] *).map { id =>
        SQL(
          """
            select
              *
            from
              asset, asset_meta, asset_meta_value
            where
              asset_meta_value.asset_id = {id}
                and
              asset_meta_value.asset_meta_id = asset_meta.id
                and
              asset.id = {id}
            """
          ).on(
            'id -> id
          ).as(Asset.withAll *)
      }
      println(t)
      Nil
    }
  }
}

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
object AssetMetaValue {
  val simple = {
    get[Long]("asset_meta_value.asset_id") ~/
    get[Long]("asset_meta_value.asset_meta_id") ~/
    get[String]("asset_meta_value.value") ^^ {
      case asset_id~asset_meta_id~meta_value => AssetMetaValue(asset_id, asset_meta_id, meta_value)
    }
  }
}
