package models

import anorm._
import anorm.SqlParser._

/**
 * Provide a convenience wrapper on top of a row of meta/value data
 */
case class MetaWrapper(_meta: AssetMeta, _value: AssetMetaValue) {
  def getAssetId(): Long = _value.asset_id
  def getMetaId(): Long = _meta.id
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
  val db = "collins"
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

  def create(mv: AssetMetaValue): Int = {
    PlayDB.withConnection(db) { implicit con =>
      SQL(
        """
          insert into asset_meta_value values (
            {amv_aid}, {amv_amid}, {value}
          )
        """
      ).on(
        'amv_aid -> mv.asset_id,
        'amv_amid -> mv.meta_id,
        'value -> mv.value
      ).executeUpdate()
    }
  }

  def findOneByAssetId(spec: Set[AssetMeta.Enum], asset_id: Long): Seq[MetaWrapper] = {
    val query = """
    select
      *
    from 
      asset_meta, asset_meta_value
    where
      asset_meta_value.asset_id = {id}
        and
      asset_meta.id = asset_meta_value.asset_meta_id
    """
    val constraints = spec.map { e =>
      "asset_meta_value.asset_meta_id = %d".format(e.id)
    }.mkString(" or ")
    val extra = constraints.isEmpty match {
      case true => ""
      case false => " and (%s)".format(constraints)
    }
    val finalQuery = query + extra
    PlayDB.withConnection(db) { implicit connection =>
      SQL(finalQuery).on('id -> asset_id).as(AssetMetaValue.withAssetMeta *)
    }
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
    PlayDB.withConnection(db) { implicit connection =>
      SQL(query).on('id -> id).as(AssetMetaValue.withAssetMeta *)
    }
  }
}


