package test

import org.specs2._
import specification._

import play.api.Play
import play.api.test.FakeApplication
import play.api.test.Helpers._

trait ApplicationSpecification extends mutable.Specification with ResourceFinder {

  def applicationSetup = {
    val app = FakeApplication()

    Play.start(app)
  }

  def applicationTeardown = {
    Play.stop()
  }

  override def map(fs: => Fragments) = Step(applicationSetup) ^ super.map(fs) ^ Step(applicationTeardown)
}
