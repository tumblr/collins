package models

import play.api.db._
import play.api.Play
import play.api.Play.current

import org.squeryl.{PrimitiveTypeMode, Session, SessionFactory}
import org.squeryl.adapters.{H2Adapter, MySQLInnoDBAdapter}
import org.squeryl.logging.{LocalH2SinkStatisticsListener, StatsSchema}

import java.io.File
import java.sql.{Connection, DriverManager}
import java.util.concurrent.atomic.AtomicReference

/**
 * Wrapper on Play DB object so models don't need an implicit application
 */
object Model {
  val name = "collins"
  type StatLogger = (Connection, LocalH2SinkStatisticsListener)
  private[this] val statLogger = new AtomicReference[StatLogger]()

  lazy val driver = current.configuration.getString("db.%s.driver".format(name)).getOrElse("org.h2.Driver")
  def adapter = driver match {
    case h2 if h2.toLowerCase.contains("h2") => new H2Adapter
    case mysql if mysql.toLowerCase.contains("mysql") => new MySQLInnoDBAdapter
  }

  lazy val isDev = Play.isDev(current)

  def initialize() {
    if (isDev && statLogger.get() == null) {
      createSinkStatisticsListener()
    }
    SessionFactory.concreteFactory = Some(
      () => statLogger.get() match {
        case null => new Session(DB.getConnection(name), adapter, None)
        case (c,l) => new Session(DB.getConnection(name), adapter, Some(l))
      }
    )
  }

  def shutdown() {
    shutdownStats(statLogger.getAndSet(null))
  }

  protected def cleanupStatFiles() {
    swallow(new File("./collins-db-usage-stats.trace.db").delete)
    swallow(new File("./collins-db-usage-stats.lock.db").delete)
    swallow(new File("./collins-db-usage-stats.h2.db").delete)
  }

  protected def shutdownStats(r: (Connection,LocalH2SinkStatisticsListener)) {
    if (r != null) {
      val (c, l) = r
      swallow(l.generateStatSummary(new File("./profileOfH2Tests.html"), 25))
      Thread.sleep(250)
      swallow(l.shutdown)
      swallow(StatsSchema.drop)
      swallow(c.close)
    }
    Session.cleanupResources
    cleanupStatFiles()
  }

  protected def createSinkStatisticsListener() = {
    Class.forName("org.h2.Driver"); // load class up
    val dir = "."
    val filename = "collins-db-usage-stats"
    val connection = DriverManager.getConnection("jdbc:h2:" + dir + "/" + filename, "sa", "")
    val s = new Session(connection, new H2Adapter)
    PrimitiveTypeMode.using(s) {
      swallow(StatsSchema.create)
    }
    statLogger.set((connection, new LocalH2SinkStatisticsListener(s)))
  }

  protected def swallow[A](f: => A) {
    try {
      f
    } catch {
      case _ =>
    }
  }

}
