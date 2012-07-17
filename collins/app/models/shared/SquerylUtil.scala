package models

import util.plugins.Cache

import play.api.Logger
import org.squeryl.{KeyedEntity, Query, Schema, Table}
import org.squeryl.dsl.QueryDsl
import org.squeryl.dsl.ast.LogicalBoolean
import org.squeryl.internals.PosoLifecycleEvent
import scala.transient
import util.Config

trait ValidatedEntity[T] extends KeyedEntity[T] {
  def validate(): Unit
  def asJson: String
  @transient private var persisted: Option[Boolean] = None
  // KeyedEntity overrides hashCode/equals with respect to isPersisted. Without the
  // forComparison/isPersisted code, two models with the same primary key are considered identical
  // (according to their PK) even if those models have different attributes. This behavior breaks
  // callbacks that need to compare an 'old' and 'new' model. forComparison provides a short-hand
  // for forcing KeyedEntity to use the class hashCode/equals instead of the custom one. This is
  // horrible and will likely break since it depends on some squeryl internals.
  def forComparison {
    persisted = Some(false)
  }
  override def isPersisted: Boolean = persisted.getOrElse(super.isPersisted)
}

trait BasicModel[T <: AnyRef] { self: Schema =>
  private[this] val logger = Logger.logger

  import org.squeryl.PrimitiveTypeMode

  protected def tableDef: Table[T] // Override
  protected def createEventName: Option[String] = None
  protected def deleteEventName: Option[String] = None

  def inTransaction[A](f: => A): A = PrimitiveTypeMode.inTransaction(f)
  protected def cacheKeys(t: T): Seq[String] = Seq()
  override def callbacks = Seq(
    afterDelete(tableDef) call(loggedInvalidation("afterDelete", _)),
    afterUpdate(tableDef) call(loggedInvalidation("afterUpdate", _)),
    afterInsert(tableDef) call(loggedInvalidation("afterInsert", _))
  )

  protected def log[A](a: => A): A = {
    if (Config.getBoolean("querylog.enabled").getOrElse(false)) {
      org.squeryl.Session.currentSession.setLogger(query => 
        if (Config.getBoolean("querylog.includeResults").getOrElse(false) || !(query startsWith "ResultSetRow")) {
          Logger.logger.info(Config.getString("querylog.prefix", "") + query)
        }
      )
    }
    a
  }

  def create(t: T): T = inTransaction {
    val newValue = tableDef.insert(t)
    createEventName.map { name =>
      util.plugins.Callback.fire(name, null, newValue)
    }
    newValue
  }

  def delete(t: T): Int // Override

  /** Optionally return a paginated query
   *
   * If either the offset or pageSize are non-zero, pagination is applied. Otherwise, the query is
   * given back without pagination being applied.
   */
  protected def optionallyPaginate[A](query: Query[A], offset: Int, pageSize: Int): Query[A] =
    if (offset == 0 && pageSize == 0) {
      query
    } else {
      query.page(offset, pageSize)
    }

  protected def loggedInvalidation(s: String, t: T) {
    logger.trace("Callback triggered: %s".format(s))
    cacheKeys(t).map { k =>
      logger.trace("Invalidating key %s".format(k))
      Cache.invalidate(k)
    }
  }

  protected def afterDeleteCallback[A](t: T)(f: => A): A = {
    val result = f
    deleteEventName.map { name =>
      util.plugins.Callback.fire(name, t, null)
    }
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
  protected def updateEventName: Option[String] = None

  def get(t: T): T

  def update(t: T): Int = inTransaction {
    try {
      val oldValue = get(t)
      tableDef.update(t)
      updateEventName.map { name =>
        oldValue.forComparison
        t.forComparison
        util.plugins.Callback.fire(name, oldValue, t)
      }
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
