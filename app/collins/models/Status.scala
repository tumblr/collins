package collins.models

import org.squeryl.PrimitiveTypeMode._

import org.squeryl.Schema

import play.api.libs.json.Format
import play.api.libs.json.JsNumber
import play.api.libs.json.JsObject
import play.api.libs.json.JsString
import play.api.libs.json.JsSuccess
import play.api.libs.json.JsValue
import play.api.libs.json.Json

import collins.models.conversions._
import collins.models.cache.Cache
import collins.models.shared.AnormAdapter
import collins.models.shared.ValidatedEntity

import collins.callbacks.CallbackDatum

case class Status(name: String, description: String, id: Int = 0)
  extends ValidatedEntity[Int] with CallbackDatum {
  override def validate() {
    require(name != null && name.length > 0, "Name must not be empty")
    require(description != null && description.length > 0, "Description must not be empty")
  }
  override def asJson: String = Json.toJson(this).toString

  // We do this to mock the former Enum stuff
  override def toString(): String = name

  override def compare(z: Any): Boolean = {
    if (z == null)
      return false
    val ar = z.asInstanceOf[AnyRef]
    if (!ar.getClass.isAssignableFrom(this.getClass))
      false
    else {
      val other = ar.asInstanceOf[Status]
      this.name == other.name && this.description == other.description
    }
  }
}

object Status extends Schema with AnormAdapter[Status] with StatusKeys {

  def Allocated = findByName("Allocated")
  def Cancelled = findByName("Cancelled")
  def Decommissioned = findByName("Decommissioned")
  def Incomplete = findByName("Incomplete")
  def Maintenance = findByName("Maintenance")
  def New = findByName("New")
  def Provisioning = findByName("Provisioning")
  def Provisioned = findByName("Provisioned")
  def Unallocated = findByName("Unallocated")

  override val tableDef = table[Status]("status")
  on(tableDef)(s => declare(
    s.id is (autoIncremented,primaryKey),
    s.name is(unique)
  ))

  def find(): List[Status] = Cache.get(findKey, inTransaction {
    from(tableDef)(s => select(s)).toList
  })

  def findById(id: Int): Option[Status] = Cache.get(findByIdKey(id), inTransaction {
    tableDef.lookup(id)
  })

  override def get(s: Status) = findById(s.id).get

  def findByName(name: String): Option[Status] = Cache.get(findByNameKey(name), inTransaction {
    tableDef.where(s =>
      s.name.toLowerCase === name.toLowerCase
    ).headOption
  })

  override def delete(s: Status): Int = inTransaction {
    afterDeleteCallback(s) {
      tableDef.deleteWhere(p => p.id === s.id)
    }
  }

  def statusNames: Set[String] = find().map(_.name).toSet

}
