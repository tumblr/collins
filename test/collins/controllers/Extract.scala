package collins.controllers

import scala.concurrent.Future
import scala.concurrent.duration._
import akka.util.Timeout

import play.api.mvc.Result
import play.api.test.{Helpers => TestHelper}

object Extract {
  def from(r: Future[Result]): (Int, Map[String,String], String) = {
    implicit val timeout = Timeout(5 seconds)
    (TestHelper.status(r), TestHelper.headers(r), TestHelper.contentAsString(r))
  }
}
