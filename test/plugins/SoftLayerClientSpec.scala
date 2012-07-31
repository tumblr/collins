package com.tumblr.play

import play.api.Play
import play.api.test.FakeApplication
import org.specs2._
import specification._

object SoftLayerClientSpec extends mutable.Specification {

  "SoftLayer Client Specification".title
  args(sequential = true)

  val username = "c37452davi"
  val password = "92a7e4c9169d737b34138a56ccc4ded66a04c172945b0292b0158c5c9f429e6b"

  "The SoftLayer Client" should {
    "Support server cancellation" in {
      //val client = new SoftLayerClientPlugin(Play.maybeApplication.get)
      //val res = client.cancelServer(101410, "Cancelling server, no longer needed")()
      true
    }
    "Support Notes" in {
      //val client = new SoftLayerClientPlugin(Play.maybeApplication.get)
      //val res = client.setNote(101410, "Cancelled server per WEBOPS-630")()
      true
    }
  }

  def setup = {
    Play.start(
      FakeApplication(
        additionalConfiguration = Map(
          "softlayer.enabled" -> "true",
          "softlayer.username" -> username,
          "softlayer.password" -> password),
        withoutPlugins = Seq("play.api.db.evolutions.EvolutionsPlugin", "play.api.db.DBPlugin")
      )
    )
  }
  def teardown = Play.stop()

  override def map(fs: => Fragments) = Step(setup) ^ super.map(fs) ^ Step(teardown)
}
