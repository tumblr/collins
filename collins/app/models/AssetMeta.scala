package models

import anorm._
import anorm.defaults._
import play.api.cache.Cache
import play.api.Play.current
import java.sql._

case class AssetMeta(
    id: Pk[java.lang.Long],
    name: String,
    priority: Int,
    label: String,
    description: String)
{
  def getId(): Long = id.get
}

object AssetMeta extends Magic[AssetMeta](Some("asset_meta")) with Dao[AssetMeta] {

  def create(metas: Seq[AssetMeta])(implicit con: Connection): Seq[AssetMeta] = {
    metas.foldLeft(List[AssetMeta]()) { case(list, meta) =>
      if (meta.id.isDefined) throw new IllegalArgumentException("Use update, id already defined")
      AssetMeta.create(meta) +: list
    }.reverse
  }

  def findById(id: Long) = Model.withConnection { implicit con =>
    AssetMeta.find("id={id}").on('id -> id).singleOption()
  }

  def getViewable(): Seq[AssetMeta] = {
    // change to use stuff in Enum
    Model.withConnection { implicit connection =>
      Cache.get[List[AssetMeta]]("AssetMeta.getViewable").getOrElse {
        logger.debug("Cache miss for AssetMeta.getViewable")
        val res = AssetMeta.find("priority > -1 order by priority asc").list()
        Cache.set("AssetMeta.getViewable", res)
        res
      }
    }
  }

  type Enum = Enum.Value
  object Enum extends Enumeration(1) {
    val ServiceTag = Value("SERVICE_TAG")
    val ChassisTag = Value("CHASSIS_TAG")
    val RackPosition = Value("RACK_POSITION")
    val PowerPort = Value("POWER_PORT")
    val SwitchPort = Value("SWITCH_PORT")
  }
}


