package test

import org.specs2._
import specification._

import play.api.Play
import play.api.test.FakeApplication
import play.api.test.Helpers._

trait ApplicationSpecification extends mutable.Specification with ResourceFinder {

  private def collinsDatabase = Map[String,String](
    "db.collins.driver" -> "org.h2.Driver",
    "db.collins.url" -> "jdbc:h2:mem:play-test-%d;IGNORECASE=TRUE".format(scala.util.Random.nextInt)
  )

  def applicationSetup = {
    val app = FakeApplication(
        additionalConfiguration = collinsDatabase,
        additionalPlugins = Seq("play.api.db.evolutions.EvolutionsPlugin")
      )

    Play.start(app)
    evolutionFor("collins")
  }

  def applicationTeardown = {
    Play.stop()
  }

  override def map(fs: => Fragments) = Step(applicationSetup) ^ super.map(fs) ^ Step(applicationTeardown)
}
