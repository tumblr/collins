package models

import play.api.db._
import play.api.Play
import play.api.Play.current

import org.squeryl.{PrimitiveTypeMode, Session, SessionFactory}
import org.squeryl.adapters.{H2Adapter, MySQLInnoDBAdapter}
import org.squeryl.logging.{LocalH2SinkStatisticsListener, StatsSchema}

import java.io.File
import java.sql.{Connection, DriverManager}

/**
 * Wrapper on Play DB object so models don't need an implicit application
 */
object Model {
  val name = "collins"

  lazy val driver = current.configuration.getString("db.%s.driver".format(name)).getOrElse("org.h2.Driver")
  def adapter = driver match {
    case h2 if h2.toLowerCase.contains("h2") => new H2Adapter
    case mysql if mysql.toLowerCase.contains("mysql") => new MySQLInnoDBAdapter
  }

  lazy val isDev = Play.isDev(current)

  def initialize() {
    SessionFactory.concreteFactory = Some(
      () => new Session(DB.getConnection(name), adapter, None)
    )
  }

  def shutdown() {
    if(Session.hasCurrentSession) {
      Session.currentSession.close
      Session.currentSession.unbindFromCurrentThread
    }
  }
}
