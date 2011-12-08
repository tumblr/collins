package models

import org.specs2.mutable._
import org.specs2.specification._
import play.api.test._
import play.api.test.MockApplication._
import play.api.db.evolutions._

trait DatabaseSpec extends Specification {
  val dataSource = Map(
    "application.name" -> "mock app",
    "db.collins.driver" -> "org.h2.Driver",
    "db.collins.url" -> "jdbc:h2:mem:play",
    "db.collins.user" -> "sa",
    "db.collins.password" -> "",
    "mock" -> "true")

  def evolveDb = {
    OfflineEvolutions.applyScript(new java.io.File("."), getClass.getClassLoader, "collins")
    val mockApp = injectGlobalMock(Nil, dataSource)
  }
  def devolveDb = {
    clearMock()
  }

  override def map(fs: => Fragments) = Step(evolveDb) ^ fs ^ Step(devolveDb)
}


