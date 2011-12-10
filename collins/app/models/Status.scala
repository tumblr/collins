package models

import anorm._
import anorm.SqlParser._

case class Status(pk: Pk[Int], name: String, description: String) extends BasicModel[Int]
object Status extends BasicQueries[Status,Int] {
  val tableName = "status"
  val simple = {
    get[Pk[Int]]("status.id") ~/
    get[String]("status.name") ~/
    get[String]("status.description") ^^ {
      case id~name~description => Status(id, name, description)
    }
  }

  type Enum = Enum.Value
  object Enum extends Enumeration(1) {
    val New, Unallocated, Allocated, Cancelled, Maintenance, Decommissioned, Incomplete = Value
  }

}
