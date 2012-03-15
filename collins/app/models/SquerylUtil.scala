package models

import util.Cache

import play.api.Logger
import org.squeryl.{KeyedEntity, Schema, Table}
import org.squeryl.dsl.QueryDsl
import org.squeryl.dsl.ast.LogicalBoolean
import org.squeryl.internals.PosoLifecycleEvent

trait ValidatedEntity[T] extends KeyedEntity[T] {
  def validate(): Unit
}

trait BasicModel[T <: AnyRef] { self: Schema =>
  private[this] val logger = Logger.logger

  import org.squeryl.PrimitiveTypeMode

  protected def tableDef: Table[T] // Override

  def inTransaction[A](f: => A): A = PrimitiveTypeMode.inTransaction(f)
  protected def cacheKeys(t: T): Seq[String] = Seq()
  override def callbacks = Seq(
    afterDelete(tableDef) call(loggedInvalidation("afterDelete", _)),
    afterUpdate(tableDef) call(loggedInvalidation("afterUpdate", _)),
    afterInsert(tableDef) call(loggedInvalidation("afterInsert", _))
  )

  protected def loggedInvalidation(s: String, t: T) {
    logger.debug("Callback triggered: %s".format(s))
    cacheKeys(t).map { k =>
      logger.debug("Invalidating key %s".format(k))
      Cache.invalidate(k)
    }
  }

  def create(t: T): T = inTransaction(tableDef.insert(t))
  def delete(t: T): Int // Override

  protected def afterDeleteCallback[A](t: T)(f: => A): A = {
    val result = f
    callbacks.filter(_.e == PosoLifecycleEvent.AfterDelete).foreach { cb =>
      cb.callback(t)
    }
    result
  }

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
