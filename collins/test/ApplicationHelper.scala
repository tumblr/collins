package test

import org.specs2._
import specification._

import play.api.Play
import play.api.test.FakeApplication

trait ApplicationHelper extends Scope with After {
  Play.start(
    FakeApplication().addPlugin("play.api.db.evolutions.EvolutionsPlugin").addPlugin("play.api.db.DBPlugin")
  )

  def after = {
    Play.stop()
  }
}
