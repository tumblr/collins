package controllers

import org.specs2.mutable._
import play.api.mvc._

class ApiSpec extends Specification with SpecHelpers {

  "The API" should {
    "Support Basic Auth" >> {
      failure
    }.pendingUntilFixed
  }

}
