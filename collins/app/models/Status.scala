package models

import anorm._
import anorm.defaults._
import java.sql._

case class Status(id: Pk[java.lang.Integer], name: String, description: String) {
  require(name != null && name.length > 0, "Name must not be empty")
  require(description != null && description.length > 0, "Description must not be empty")
  def getId(): Int = id.get
}
object Status extends Magic[Status](Some("status")) with Dao[Status] {

  def apply(name: String, description: String) = {
    new Status(NotAssigned, name, description)
  }

  def findById(id: Int): Option[Status] = Model.withConnection { implicit con =>
    Status.find("id={id}").on('id -> id).first()
  }

  def create(statuses: Seq[Status])(implicit con: Connection): Seq[Status] = {
    statuses.foldLeft(List[Status]()) { case(list, status) =>
      if (status.id.isDefined) throw new IllegalArgumentException("Can only create for status with id 0")
      Status.create(status) +: list
    }.reverse
  }

  type Enum = Enum.Value
  object Enum extends Enumeration(1) {
    val New, Unallocated, Allocated, Cancelled, Maintenance, Decommissioned, Incomplete = Value
  }

}
