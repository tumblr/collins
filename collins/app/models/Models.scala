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
