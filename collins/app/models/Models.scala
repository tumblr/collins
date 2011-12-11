package models

import java.util.Date

import play.api.db._
import play.api.Play.current

import anorm._
import anorm.defaults._
import anorm.SqlParser._

import java.sql._

/**
 * Wrapper on Play DB object so models don't need an implicit application
 */
object Model {
  val name = "collins"
  def withConnection[A](block: Connection => A): A = {
    DB.withConnection(name)(block)(current)
  }
  def withTransaction[A](block: Connection => A): A = {
    DB.withTransaction(name)(block)(current)
  }
}

trait Dao[T] { self: Magic[T] => 
  protected val logger = play.api.Logger.logger

  override def extendExtractor[C](f: (Manifest[C] => Option[ColumnTo[C]])): PartialFunction[Manifest[C], Option[ColumnTo[C]]] = {
    case m if 
       // Fixed for Id
       m <:< manifest[Id[Any]] ||
       // Added support for checking Pk
       m <:< manifest[Pk[Any]] => {
      val typeParam = m.typeArguments
        .headOption
        .collect { case m: ClassManifest[_] => m }
        .getOrElse(implicitly[Manifest[Any]])
      f(typeParam.asInstanceOf[Manifest[C]]).map(mapper => ColumnTo.rowToPk(mapper)).asInstanceOf[Option[ColumnTo[C]]]
      // OR: getExtractor(typeParam).map(mapper => ColumnTo.rowToPk(mapper)).asInstanceOf[Option[ColumnTo[C]]]
    }
    case _ if false => None
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
  protected val logger = play.api.Logger.logger
  val db: String = "collins"
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

  def batchCreate(items: Seq[T])(implicit con: Connection): Int
  def create(item: T)(implicit con: Connection): Int = batchCreate(List(item))

  //def batchDelete(items: Seq[PK])(implicit con: Connection): Int
  //def delete(item: PK)(implicit con: Connection): Int = batchDelete(List(item)) 

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
