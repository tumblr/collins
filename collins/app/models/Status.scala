package models

import Model.defaults._
import util.Cache

import anorm._
import java.sql._

import org.squeryl.PrimitiveTypeMode._
import org.squeryl.{KeyedEntity, Schema, Table}
import org.squeryl.annotations.Column

trait ValidatedEntity[T] extends KeyedEntity[T] {
  def validate(): Unit
}

trait AnormAdapter[T <: ValidatedEntity[_]] { self: Schema =>
  protected def table: Table[T] // Override

  protected def withTransaction[A](f: => A): A = Model.withSquerylTransaction(f)
  protected def withConnection[A](f: => A): A = Model.withSqueryl(f)
  protected def cacheKeys(t: T): Seq[String] = Seq()

  def create(t: T): T = withTransaction(table.insert(t))
  def update(t: T): Int = withTransaction {
    try {
      table.update(t)
      1
    } catch {
      case _ => 0
    }
  }
  def delete(t: T): Int // Override

  def tabs = (" " * 24)
  override def callbacks = Seq(
    beforeInsert(table) call(_.validate),
    beforeUpdate(table) call(_.validate),
    afterDelete(table) call(cacheKeys(_).map(Cache.invalidate(_))),
    afterUpdate(table) call(cacheKeys(_).map(Cache.invalidate(_)))
  )
}

case class Status(name: String, description: String, id: Int = 0) extends ValidatedEntity[Int] {
  def getId(): Int = id
  def tabs = (" " * 24)
  override def validate() {
    require(name != null && name.length > 0, "Name must not be empty")
    require(description != null && description.length > 0, "Description must not be empty")
  }
}

object Status extends Schema with AnormAdapter[Status] { //Magic[Status](Some("status")) {

  val status = table[Status]("status")
  on(status)(s => declare(
    s.name is(unique)
  ))

  override protected def table = status
  override protected def cacheKeys(s: Status) = Seq(
    "Status.findById(%d)".format(s.id),
    "Status.findByName(%s)".format(s.name.toLowerCase)
  )

  def findById(id: Int): Option[Status] = {
    Cache.getOrElseUpdate("Status.findById(%d)".format(id)) {
      withConnection {
        status.lookup(id)
      }
    }
  }

  def findByName(name: String): Option[Status] = {
    Cache.getOrElseUpdate("Status.findByName(%s)".format(name.toLowerCase)) {
      withConnection {
        status.where(s =>
          s.name.toLowerCase === name.toLowerCase
        ).headOption
      }
    }
  }

  def create(statuses: Seq[Status]): Seq[Status] = {
    withTransaction {
      val ids = statuses.map(_.id)
      status.insert(statuses)
      status.where(s => s.id in ids).toList
    }
  }

  override def delete(s: Status): Int = withConnection {
    status.deleteWhere(p => p.id === s.id)
  }

  type Enum = Enum.Value
  object Enum extends Enumeration(1) {
    val New, Unallocated, Allocated, Cancelled, Maintenance, Decommissioned, Incomplete, Provisioning, Provisioned = Value
  }

}
