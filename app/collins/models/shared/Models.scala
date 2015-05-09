package collins.models.shared

import org.squeryl.Session
import org.squeryl.SessionFactory
import org.squeryl.adapters.H2Adapter
import org.squeryl.adapters.MySQLInnoDBAdapter

import play.api.Logger
import play.api.Play
import play.api.Play.current
import play.api.db.DB

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
      Logger.debug("closing squeryl session")

      Session.currentSession.close
      Session.currentSession.unbindFromCurrentThread
    }
  }
}
