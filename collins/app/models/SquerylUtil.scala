package models

import util.Cache

import org.squeryl.{KeyedEntity, Schema, Table}

trait ValidatedEntity[T] extends KeyedEntity[T] {
  def validate(): Unit
}

trait BasicModel[T] { self: Schema =>
  import org.squeryl.PrimitiveTypeMode

  protected def tableDef: Table[T] // Override

  def inTransaction[A](f: => A): A = PrimitiveTypeMode.inTransaction(f)
  protected def cacheKeys(t: T): Seq[String] = Seq()
  override def callbacks = Seq(
    afterDelete(tableDef) call(cacheKeys(_).map(Cache.invalidate(_))),
    afterUpdate(tableDef) call(cacheKeys(_).map(Cache.invalidate(_))),
    afterInsert(tableDef) call(cacheKeys(_).map(Cache.invalidate(_)))
  )

  def create(t: T): T = inTransaction(tableDef.insert(t))
  def delete(t: T): Int // Override

  protected def getOrElseUpdate[T <: AnyRef](key: String)(op: => T)(implicit m: Manifest[T]): T = {
    Cache.getOrElseUpdate(key) {
      inTransaction {
        op
      }
    }(m)
  }
}

trait AnormAdapter[T <: ValidatedEntity[_]] extends BasicModel[T] { self: Schema =>
  def update(t: T): Int = inTransaction {
    try {
      tableDef.update(t)
      1
    } catch {
      case _ => 0
    }
  }

  override def callbacks = super.callbacks ++ Seq(
    beforeInsert(tableDef) call(_.validate),
    beforeUpdate(tableDef) call(_.validate)
  )
}
