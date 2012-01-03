package test

import org.specs2._
import specification._

import play.api.Play
import play.api.test.FakeApplication

trait ApplicationSpecification extends mutable.Specification {

  def applicationSetup = {
    Play.start(
      FakeApplication().addPlugin("play.api.db.evolutions.EvolutionsPlugin").addPlugin("play.api.db.DBPlugin")
    )
  }

  def applicationTeardown = {
    Play.stop()
  }

  override def map(fs: => Fragments) = Step(applicationSetup) ^ super.map(fs) ^ Step(applicationTeardown)
}
