package collins.controllers

import play.api.mvc.AsyncResult
import play.api.mvc.Result
import play.api.test.{Helpers => TestHelper}

object Extract {
  def from(r: Result): (Int, Map[String,String], String) = {
    val newRes = r match {
      case AsyncResult(promise) => TestHelper.await(promise)
      case _ => r
    }
    (TestHelper.status(newRes), TestHelper.headers(newRes), TestHelper.contentAsString(newRes))
  }
}
