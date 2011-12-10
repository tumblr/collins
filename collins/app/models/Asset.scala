package models

import anorm._
import anorm.SqlParser._

import java.util.Date

case class Asset(
    pk: Pk[Long],
    secondaryId: String,
    status: Int,
    assetType: Int,
    created: Date, updated: Option[Date], deleted: Option[Date])
  extends BasicModel[Long]
{
  def isNew(): Boolean = {
    status == models.Status.Enum.New.id
  }
  def getStatus(): Status = {
    Status.findById(status).get
  }
  def getType(): AssetType = {
    AssetType.findById(assetType).get
  }
  def getAttribute(spec: AssetMeta.Enum): Option[MetaWrapper] = {
    AssetMetaValue.findOneByAssetId(Set(spec), id).toList match {
      case Nil => None
      case one :: Nil =>
        Some(one)
      case other =>
        throw new IndexOutOfBoundsException("Expected one value, if any")
    }
  }
  def getAttributes(specs: Set[AssetMeta.Enum] = Set.empty): List[MetaWrapper] = {
    specs.isEmpty match {
      case true =>
        AssetMetaValue.findAllByAssetId(id).toList
      case false =>
        AssetMetaValue.findOneByAssetId(specs, id).toList
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

  def update(asset: Asset): Int = {
    PlayDB.withConnection(db) { implicit con =>
      SQL(
        """
          update asset
          set status = {status}, updated = CURRENT_TIMESTAMP
          where id = {id}
        """
      ).on(
        'id -> asset.id,
        'status -> asset.status
      ).executeUpdate()
    }
  }
  def findBySecondaryId(id: String): Option[Asset] = {
    val query = "select * from asset where secondary_id = {id}"
    PlayDB.withConnection(db) { implicit connection =>
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
      (fragment, (Symbol(id), toParameterValue(v)))
    }
    val nq = query + params.map { _._1 }.mkString(" and ")
    PlayDB.withConnection(db) { implicit connection =>
      val ids = SQL(
        nq
      ).on(
        params.map{ _._2 }:_*
      ).as(scalar[Long] *)
      ids.isEmpty match {
        case true => Seq.empty
        case false => Asset.findByIds(ids)
      }
    }
  }
}


