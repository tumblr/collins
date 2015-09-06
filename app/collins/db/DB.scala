package collins.db

import org.squeryl.Session
import org.squeryl.SessionFactory
import org.squeryl.adapters.H2Adapter
import org.squeryl.adapters.MySQLInnoDBAdapter

import play.api.Application
import play.api.Logger
import play.api.db.{ DB => PDB }

/**
 * Wrapper on Play DB object so models don't need an implicit application
 */
object DB {
  protected[this] val logger = Logger(getClass)
  val name = "collins"

  private[this] def createAdapter(driver: String) = driver match {
    case h2 if h2.toLowerCase.contains("h2")          => new H2Adapter
    case mysql if mysql.toLowerCase.contains("mysql") => new MySQLInnoDBAdapter
  }

  def initialize(app: Application) {
    logger.debug("Initializing Session factory creation closure")

    val driver = app.configuration.getString("db.%s.driver".format(name)).getOrElse("org.h2.Driver")
    logger.debug("Using driver %s".format(driver))

    val adapter = createAdapter(driver)

    // NOTE: Synchronized to ensure concrete factory is visible to actor-systems
    // immediately on start up.
    SessionFactory.synchronized {
      SessionFactory.concreteFactory = Some(
      () => new Session(PDB.getConnection(name)(app), adapter, None))
    }
  }

  def shutdown() {
    if (Session.hasCurrentSession) {
      logger.error("Close Squeryl session during shutdown, this should only happen in an error.")

      Session.currentSession.close
      Session.currentSession.unbindFromCurrentThread
    }
    SessionFactory.concreteFactory = None
  }
}
