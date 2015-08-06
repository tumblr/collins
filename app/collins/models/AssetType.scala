package collins.models

import org.squeryl.PrimitiveTypeMode._
import org.squeryl.Schema

import play.api.libs.json.Format
import play.api.libs.json.JsObject
import play.api.libs.json.JsSuccess
import play.api.libs.json.JsValue
import play.api.libs.json.Json

import collins.util.Stats

import collins.models.cache.Cache
import collins.models.shared.AnormAdapter
import collins.models.shared.ValidatedEntity

case class AssetType(name: String, label: String, id: Int = 0) extends ValidatedEntity[Int] {
  override def validate() {
    require(name != null && name.length > 0, "Name must not be empty")
  }
  override def asJson: String =
    Json.stringify(AssetType.AssetTypeFormat.writes(this))
  // We do this to mock the former Enum stuff
  override def toString(): String = name
}

object AssetType extends Schema with AnormAdapter[AssetType] with AssetTypeKeys {

  override val tableDef = table[AssetType]("asset_type")
  val reservedNames = List("SERVER_NODE","SERVER_CHASSIS","RACK","SWITCH","ROUTER","POWER_CIRCUIT","POWER_STRIP","DATA_CENTER","CONFIGURATION")
  on(tableDef)(a => declare(
    a.id is(autoIncremented,primaryKey),
    a.name is(unique)
  ))

  implicit object AssetTypeFormat extends Format[AssetType] {
    override def reads(json: JsValue) = Stats.time("AssetType.Reads") {
      JsSuccess(AssetType(
        (json \ "NAME").as[String],
        (json \ "LABEL").as[String],
        (json \ "ID").asOpt[Int].getOrElse(0)
      ))
    }
    override def writes(at: AssetType) = Stats.time("AssetType.Writes") {
      JsObject(Seq(
        "ID" -> Json.toJson(at.id),
        "NAME" -> Json.toJson(at.name),
        "LABEL" -> Json.toJson(at.label)
      ))
    }
  }

  def findById(id: Int): Option[AssetType] = Cache.get(findByIdKey(id), inTransaction {
    tableDef.lookup(id)
  })

  override def get(a: AssetType) = findById(a.id).get

  def find(): List[AssetType] = Cache.get(findKey, inTransaction {
    from(tableDef)(at => select(at)).toList
  })

  def findByName(name: String): Option[AssetType] = Cache.get(findByNameKey(name), inTransaction {
    tableDef.where(a =>
      a.name.toLowerCase === name.toLowerCase
    ).headOption
  })

  override def delete(a: AssetType): Int = inTransaction {
    afterDeleteCallback(a) {
      tableDef.deleteWhere(p => p.id === a.id)
    }
  }

  def typeNames: Set[String] = find().map(_.name).toSet

  def isServerNode(at: AssetType): Boolean = ServerNode.map(_.id).filter(_.equals(at.id)).isDefined

  def ServerNode = findByName("SERVER_NODE")
  def Configuration = findByName("CONFIGURATION")
  def isSystemType(atype: AssetType) = reservedNames.contains(atype.name.toUpperCase)

}
