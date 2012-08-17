package util

import play.api.mvc.Headers

import ApiVersion._

import org.specs2._

class VersionRouterSpec extends mutable.Specification {

  val map: PartialFunction[ApiVersion,String] = {
    case `1.1` => "A"
    case `1.2` => "B"
  }

  case class FakeHeaders(headers: Map[String, Seq[String]]) extends Headers {
    def getAll(key: String) = headers(key)
    def keys = headers.keys.toSet
  }

  "version router" should {
    "route to correct version" in {
      val heads = FakeHeaders(Map("Accept" -> List("com.tumblr.collins;version=1.2", "foo", "com.tumblr.collins")))
      VersionRouter(heads)(map) must_== "B"

    }
    "default route on missing header" in {      
      VersionRouter(FakeHeaders(Map[String, Seq[String]]()))(map) must_== map(ApiVersion.defaultVersion)
    }
    "throw exception on malformed header" in {
      VersionRouter(FakeHeaders(Map("Accept" -> List("HASFIAFSHAF"))))(map) must throwA[Exception]
    }
    "throw exception on invalid version" in {
      val heads = FakeHeaders(Map("Accept" -> List("com.tumblr.collins;version=26.12", "foo", "com.tumblr.collins")))
      VersionRouter(heads)(map) must throwA[Exception]
    }
  }

}
