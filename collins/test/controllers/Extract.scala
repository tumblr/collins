package controllers

import play.api.mvc._
import play.api.test.{Helpers => TestHelper}

object Extract {
  def from(r: Result): (Int, Map[String,String], String) = {
    (TestHelper.status(r), TestHelper.headers(r), TestHelper.contentAsString(r))
  }
}
