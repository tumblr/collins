package controllers

import org.specs2.mutable._
import play.api.mvc._

class ApiSpec extends Specification with SpecHelpers {

  val user = getLoggedInUser("infra")
  val api = getApi(user)

  "The API" should {
    "Support Basic Auth" >> {
      println(api.asset("hello"))
      true
    }
  }

}
