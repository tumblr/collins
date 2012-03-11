package models

import util.Cache

import org.squeryl.{KeyedEntity, Schema, Table}

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

  override def callbacks = Seq(
    beforeInsert(table) call(_.validate),
    beforeUpdate(table) call(_.validate),
    afterDelete(table) call(cacheKeys(_).map(Cache.invalidate(_))),
    afterUpdate(table) call(cacheKeys(_).map(Cache.invalidate(_)))
  )
}
