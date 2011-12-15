package models

import anorm._
import anorm.defaults._

import util.LshwRepresentation

import java.util.Date
import java.sql._

case class Asset(
    id: Pk[java.lang.Long],
    secondary_id: String,
    status: Int,
    asset_type: Int,
    created: Date, updated: Option[Date], deleted: Option[Date])
{
  require(secondary_id != null && secondary_id.length > 0, "Secondary id must not be empty")
  require(Asset.SecondaryId(secondary_id).matches, "Secondary id must be alpha numeric")

  def getId(): Long = id.get
  def isNew(): Boolean = {
    status == models.Status.Enum.New.id
  }
  def getStatus(): Status = {
    Status.findById(status).get
  }
  def getType(): AssetType = {
    AssetType.findById(asset_type).get
  }
  def getAttribute(spec: AssetMeta.Enum): Option[MetaWrapper] = {
    AssetMetaValue.findOneByAssetId(Set(spec), id.get).toList match {
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
        AssetMetaValue.findAllByAssetId(id.get).toList
      case false =>
        AssetMetaValue.findOneByAssetId(specs, id.get).toList
    }
  }
}

object Asset extends Magic[Asset](Some("asset")) with Dao[Asset] {

  val SecondaryId = """[A-Za-z0-9\-_]+""".r.pattern.matcher(_)

  def apply(secondary_id: String, status: Status.Enum, asset_type: AssetType.Enum) = {
    new Asset(NotAssigned, secondary_id, status.id, asset_type.id, new Date(), None, None)
  }

  def create(assets: Seq[Asset])(implicit con: Connection): Seq[Asset] = {
    assets.foldLeft(List[Asset]()) { case(list, asset) =>
      if (asset.id.isDefined) throw new IllegalArgumentException("id of asset must be NotAssigned")
      Asset.create(asset) +: list
    }.reverse
  }

  def findById(id: Long): Option[Asset] = Model.withConnection { implicit con =>
    Asset.find("id={id}").on('id -> id).singleOption()
  }
  def findBySecondaryId(id: String): Option[Asset] = Model.withConnection { implicit con =>
    Asset.find("secondary_id={id}").on('id -> id).first()
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
    val subquery = query + params.map { _._1 }.mkString(" and ")
    Model.withConnection { implicit connection =>
      Asset.find("select * from asset WHERE id in (%s)".format(subquery)).on(
        params.map(_._2):_*
      ).list()
    }
  }
}
