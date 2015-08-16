package collins.models.shared

import org.squeryl.KeyedEntity
import org.squeryl.PrimitiveTypeMode
import org.squeryl.Query
import org.squeryl.Schema
import org.squeryl.Table
import org.squeryl.internals.PosoLifecycleEvent

import play.api.Logger

import collins.callbacks.Callback
import collins.callbacks.CallbackDatum
import collins.callbacks.CallbackDatumHolder

import collins.models.cache.Cache

trait Keys[T <: AnyRef] {
  def cacheKeys(t: T): Seq[String]
}

trait ValidatedEntity[T] extends KeyedEntity[T] {
  def validate(): Unit
  def asJson: String
}

trait BasicModel[T <: CallbackDatum] { self: Schema with Keys[T] =>
  private[this] val logger = Logger(getClass)

  import org.squeryl.PrimitiveTypeMode

  protected def tableDef: Table[T] // Override
  protected def createEventName: Option[String] = None
  protected def deleteEventName: Option[String] = None

  def inTransaction[A](f: => A): A = PrimitiveTypeMode.inTransaction(f)

  override def callbacks = Seq(
    afterDelete(tableDef) call (loggedInvalidation("afterDelete", _)),
    afterUpdate(tableDef) call (loggedInvalidation("afterUpdate", _)),
    afterInsert(tableDef) call (loggedInvalidation("afterInsert", _)))

  protected def log[A](a: => A): A = {
    if (QueryLogConfig.enabled) {
      org.squeryl.Session.currentSession.setLogger(query =>
        if (QueryLogConfig.includeResults || !(query startsWith "ResultSetRow")) {
          Logger.logger.info(QueryLogConfig.prefix + query)
        })
    }
    a
  }

  def create(t: T): T = inTransaction {
    val newValue = tableDef.insert(t)
    createEventName.map { name =>
      Callback.fire(name, CallbackDatumHolder(None), CallbackDatumHolder(Some(newValue)))
    }
    newValue
  }

  def delete(t: T): Int // Override

  /**
   * Optionally return a paginated query
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
    logger.trace("Callback triggered: %s - %s".format(getClass.getName, s))
    cacheKeys(t).map { k =>
      Cache.invalidate(k)
    }
  }
  protected def afterDeleteCallback[A](t: T)(f: => A): A = {
    val result = f
    deleteEventName.map { name =>
      Callback.fire(name, CallbackDatumHolder(Some(t)), CallbackDatumHolder(None))
    }
    callbacks.filter(_.e == PosoLifecycleEvent.AfterDelete).foreach { cb =>
      cb.callback(t)
    }
    result
  }
}

trait AnormAdapter[T <: ValidatedEntity[_] with CallbackDatum] extends BasicModel[T] { self: Schema with Keys[T] =>
  protected def updateEventName: Option[String] = None

  def get(t: T): T

  def update(t: T): Int = inTransaction {
    try {
      val oldValue = get(t)
      tableDef.update(t)
      updateEventName.map {
        Callback.fire(_, CallbackDatumHolder(Some(oldValue)), CallbackDatumHolder(Some(t)))
      }
      1
    } catch {
      case _: Throwable => 0
    }
  }

  override def callbacks = super.callbacks ++ Seq(
    beforeInsert(tableDef) call (_.validate),
    beforeUpdate(tableDef) call (_.validate))
}
