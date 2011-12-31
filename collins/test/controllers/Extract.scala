package controllers

import play.api.mvc._
import play.api.test.{Helpers => TestHelper}
import play.api.libs.concurrent.Promise

object Extract {
  def from(r: Result): (Int, Map[String,String], String) = {
    val newRes = r match {
      case AsyncResult(promise) => TestHelper.await(promise)
      case _ => r
    }
    (TestHelper.status(newRes), TestHelper.headers(newRes), TestHelper.contentAsString(newRes))
  }
}
