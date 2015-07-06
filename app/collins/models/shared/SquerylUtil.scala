package collins.models.shared

import org.squeryl.KeyedEntity
import org.squeryl.PrimitiveTypeMode
import org.squeryl.Query
import org.squeryl.Schema
import org.squeryl.Table
import org.squeryl.internals.PosoLifecycleEvent

import play.api.Logger

import collins.callbacks.Callback

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
  private[this] val logger = Logger(getClass)

  import org.squeryl.PrimitiveTypeMode

  protected def tableDef: Table[T] // Override
  protected def createEventName: Option[String] = None
  protected def deleteEventName: Option[String] = None

  def inTransaction[A](f: => A): A = PrimitiveTypeMode.inTransaction(f)

  protected def log[A](a: => A): A = {
    if (QueryLogConfig.enabled) {
      org.squeryl.Session.currentSession.setLogger(query => 
        if (QueryLogConfig.includeResults || !(query startsWith "ResultSetRow")) {
          Logger.logger.info(QueryLogConfig.prefix + query)
        }
      )
    }
    a
  }

  def create(t: T): T = inTransaction {
    val newValue = tableDef.insert(t)
    createEventName.map { name =>
      Callback.fire(name, null, newValue)
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

  protected def afterDeleteCallback[A](t: T)(f: => A): A = {
    val result = f
    deleteEventName.map { name =>
      Callback.fire(name, t, null)
    }
    callbacks.filter(_.e == PosoLifecycleEvent.AfterDelete).foreach { cb =>
      cb.callback(t)
    }
    result
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
        Callback.fire(name, oldValue, t)
      }
      1
    } catch {
      case _: Throwable => 0
    }
  }

  override def callbacks = Seq(
    beforeInsert(tableDef) call(_.validate),
    beforeUpdate(tableDef) call(_.validate)
  )
}
