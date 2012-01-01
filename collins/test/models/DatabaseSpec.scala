package models

import org.specs2.mutable._
import org.specs2.specification._
import play.api._
import play.api.test._

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
    Play.start(
      FakeApplication().addPlugin("play.api.db.evolutions.EvolutionsPlugin").addPlugin("play.api.db.DBPlugin")
    )
  }
  def devolveDb = {
    Play.stop()
  }

  override def map(fs: => Fragments) = Step(evolveDb) ^ super.map(fs) ^ Step(devolveDb)
}


