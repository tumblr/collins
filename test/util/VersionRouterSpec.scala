package util

import play.api.mvc.Headers

import ApiVersion._

import org.specs2._

class VersionRouterSpec extends mutable.Specification {

  val map = Map(
    `2.0` -> "A",
    `2.1` -> "B"
  )

  case class FakeHeaders(headers: Map[String, Seq[String]]) extends Headers {
    def getAll(key: String) = headers(key)
    def keys = headers.keys.toSet
  }

  "version router" should {
    "route to correct version" in {
      val heads = FakeHeaders(Map("Accept" -> List("com.tumblr.collins;version=2.1", "foo", "com.tumblr.collins")))
      VersionRouter(map)(heads) must_== "B"

    }
    "default route on missing header" in {      
      VersionRouter(map)(FakeHeaders(Map[String, Seq[String]]())) must_== map(ApiVersion.defaultVersion)
    }
    "throw exception on malformed header" in {
      VersionRouter(map)(FakeHeaders(Map("Accept" -> List("HASFIAFSHAF")))) must throwA[Exception]
    }
    "throw exception on invalid version" in {
      val heads = FakeHeaders(Map("Accept" -> List("com.tumblr.collins;version=26.12", "foo", "com.tumblr.collins")))
      VersionRouter(map)(heads) must throwA[Exception]
    }
  }

}
