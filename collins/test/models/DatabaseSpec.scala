package models

import org.specs2.mutable._
import org.specs2.specification._
import play.api.test._
import play.api.test.MockApplication._
import play.api.db.evolutions._

object DatabaseSpec {
  val dataSource = Map(
    "application.name" -> "mock app",
    "db.collins.driver" -> "org.h2.Driver",
    "db.collins.url" -> "jdbc:h2:mem:play",
    "db.collins.user" -> "sa",
    "db.collins.password" -> "",
    "crypto.key" -> "foobarbaz",
    "ipmi.gateway" -> "10.0.0.1",
    "ipmi.netmask" -> "255.255.224.0",
    "mock" -> "true")
}

trait DatabaseSpec extends Specification {
  def evolveDb = {
    OfflineEvolutions.applyScript(new java.io.File("."), getClass.getClassLoader, "collins")
    val mockApp = injectGlobalMock(Nil, DatabaseSpec.dataSource)
  }
  def devolveDb = {
    clearMock()
  }

  override def map(fs: => Fragments) = Step(evolveDb) ^ fs ^ Step(devolveDb)
}


