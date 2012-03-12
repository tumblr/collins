package models

import java.util.Date

import play.api.db._
import play.api.Play.current

import anorm._
import anorm.SqlParser._

import java.sql._
import org.squeryl.{Session, SessionFactory}
import org.squeryl.adapters.{H2Adapter, MySQLInnoDBAdapter}

/**
 * Wrapper on Play DB object so models don't need an implicit application
 */
object Model {
  import org.squeryl.PrimitiveTypeMode
  val name = "collins"

  lazy val driver = current.configuration.getString("db.%s.driver".format(name)).getOrElse("org.h2.Driver")
  def adapter = driver match {
    case h2 if h2.toLowerCase.contains("h2") => new H2Adapter
    case mysql if mysql.toLowerCase.contains("mysql") => new MySQLInnoDBAdapter
  }

  def withSqueryl[A](c: Connection)(f: Session => A): A = {
    val session = new Session(c, adapter)
    PrimitiveTypeMode.using(session)(f(session))
  }
  def withSqueryl[A](f: => A): A = {
    withConnection { con =>
      PrimitiveTypeMode.using(new Session(con, adapter))(f)
    }
  }

  def withSquerylTransaction[A](f: => A): A = {
    withTransaction { con =>
      PrimitiveTypeMode.using(new Session(con, adapter))(f)
    }
  }

  def withConnection[A](block: Connection => A): A = {
    DB.withConnection(name)(block)(current)
  }
  def withTransaction[A](block: Connection => A): A = {
    DB.withTransaction(name)(block)(current)
  }

}
