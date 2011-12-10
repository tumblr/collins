package models

import java.util.Date

import play.api.db._
import play.api.Play.current

import anorm._
import anorm.SqlParser._

/**
 * Wrapper on Play DB object so models don't need an implicit application
 */
object PlayDB {
  import java.sql._
  def withConnection[A](name: String)(block: Connection => A): A = {
    DB.withConnection(name)(block)(current)
  }
}

/**
 * Convenience wrapper so models with a Pk[id] have a 'normal' interface
 */
trait BasicModel[T] {
  val pk: Pk[T]
  lazy val id: T = pk.get
}

/**
 * Mixin available for models with a pk
 */
trait BasicQueries[T,PK] { self =>
  protected val db: String = "collins"
  protected val logger = play.api.Logger.logger
  val tableName: String
  val simple: Parser[T]

  def findById(id: PK): Option[T] = {
    val query = "select * from %s where id = {id}".format(tableName)
    findById(id, query)
  }
  def findByIds(ids: Seq[PK]): Seq[T] = {
    val query = "select * from %s where id in (%s)".format(tableName, ids.mkString(","))
    DB.withConnection(db) { implicit connection =>
      SQL(query).as(simple *)
    }
  }

  protected def nextId(seq: String): Long = {
    val query = "select next value for %s".format(seq)
    DB.withConnection(db) { implicit connection =>
      SQL(query).as(scalar[Long])
    }
  }
  protected def findById(id: PK, query: String): Option[T] = {
    DB.withConnection(db) { implicit connection =>
      SQL(query).on('id -> id).as(simple ?)
    }
  }
}
