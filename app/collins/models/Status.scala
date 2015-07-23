package collins.models

import scala.math.BigDecimal.int2bigDecimal

import org.squeryl.PrimitiveTypeMode.__thisDsl
import org.squeryl.PrimitiveTypeMode.from
import org.squeryl.PrimitiveTypeMode.int2ScalarInt
import org.squeryl.PrimitiveTypeMode.select
import org.squeryl.PrimitiveTypeMode.string2ScalarString
import org.squeryl.Schema

import play.api.libs.json.Format
import play.api.libs.json.JsNumber
import play.api.libs.json.JsObject
import play.api.libs.json.JsString
import play.api.libs.json.JsSuccess
import play.api.libs.json.JsValue
import play.api.libs.json.Json

import collins.models.shared.AnormAdapter
import collins.models.shared.ValidatedEntity

case class Status(name: String, description: String, id: Int = 0) extends ValidatedEntity[Int] {
  override def validate() {
    require(name != null && name.length > 0, "Name must not be empty")
    require(description != null && description.length > 0, "Description must not be empty")
  }
  override def asJson: String =
    Json.stringify(Status.StatusFormat.writes(this))

  // We do this to mock the former Enum stuff
  override def toString(): String = name
}

object Status extends Schema with AnormAdapter[Status] {

  def Allocated = Status.findByName("Allocated")
  def Cancelled = Status.findByName("Cancelled")
  def Decommissioned = Status.findByName("Decommissioned")
  def Incomplete = Status.findByName("Incomplete")
  def Maintenance = Status.findByName("Maintenance")
  def New = Status.findByName("New")
  def Provisioning = Status.findByName("Provisioning")
  def Provisioned = Status.findByName("Provisioned")
  def Unallocated = Status.findByName("Unallocated")

  implicit object StatusFormat extends Format[Status] {
    override def reads(json: JsValue) = JsSuccess(Status(
      (json \ "NAME").as[String],
      (json \ "DESCRIPTION").as[String],
      (json \ "ID").as[Int]
    ))
    override def writes(status: Status) = JsObject(Seq(
      "ID" -> JsNumber(status.id),
      "NAME" -> JsString(status.name),
      "DESCRIPTION" -> JsString(status.description)
    ))
  }

  override val tableDef = table[Status]("status")
  on(tableDef)(s => declare(
    s.id is (autoIncremented,primaryKey),
    s.name is(unique)
  ))

  def find(): List[Status] = inTransaction {
    from(tableDef)(s => select(s)).toList
  }
  
  def findById(id: Int): Option[Status] = inTransaction {
    tableDef.lookup(id)
  }

  override def get(s: Status) = findById(s.id).get

  def findByName(name: String): Option[Status] = inTransaction {
    tableDef.where(s =>
      s.name.toLowerCase === name.toLowerCase
    ).headOption
  }

  override def delete(s: Status): Int = inTransaction {
    afterDeleteCallback(s) {
      tableDef.deleteWhere(p => p.id === s.id)
    }
  }

  def statusNames: Set[String] = find().map(_.name).toSet

}
