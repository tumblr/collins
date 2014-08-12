package models

import play.api.libs.json._

import org.squeryl.PrimitiveTypeMode._
import org.squeryl.{Schema, Table}

case class AssetType(name: String, label: String, id: Int = 0) extends ValidatedEntity[Int] {
  def getId(): Int = id
  override def validate() {
    require(name != null && name.length > 0, "Name must not be empty")
  }
  override def asJson: String =
    Json.stringify(AssetType.AssetTypeFormat.writes(this))
  // We do this to mock the former Enum stuff
  override def toString(): String = name
}

object AssetType extends Schema with AnormAdapter[AssetType] {

  override val tableDef = table[AssetType]("asset_type")
  val reservedNames = List("SERVER_NODE","SERVER_CHASSIS","RACK","SWITCH","ROUTER","POWER_CIRCUIT","POWER_STRIP","DATA_CENTER","CONFIGURATION")
  on(tableDef)(a => declare(
    a.id is(autoIncremented,primaryKey),
    a.name is(unique)
  ))

  implicit object AssetTypeFormat extends Format[AssetType] {
    override def reads(json: JsValue) = AssetType(
      (json \ "NAME").as[String],
      (json \ "LABEL").as[String],
      (json \ "ID").asOpt[Int].getOrElse(0)
    )
    override def writes(at: AssetType) = JsObject(Seq(
      "ID" -> Json.toJson(at.id),
      "NAME" -> Json.toJson(at.name),
      "LABEL" -> Json.toJson(at.label)
    ))
  }

  override def cacheKeys(a: AssetType) = Seq(
    "AssetType.findById(%d)".format(a.id),
    "AssetType.findByName(%s)".format(a.name.toUpperCase),
    "AssetType.find"
  )

  def findById(id: Int): Option[AssetType] =
    getOrElseUpdate("AssetType.findById(%d)".format(id)) {
      tableDef.lookup(id)
    }

  override def get(a: AssetType) = findById(a.id).get

  def find(): List[AssetType] = getOrElseUpdate("AssetType.find") {
    from(tableDef)(at => select(at)).toList
  }

  def findByName(name: String): Option[AssetType] =
    getOrElseUpdate("AssetType.findByName(%s)".format(name.toUpperCase)) {
      tableDef.where(a =>
        a.name.toLowerCase === name.toLowerCase
      ).headOption
    }

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
