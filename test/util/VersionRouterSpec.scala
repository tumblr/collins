package util

import play.api.mvc.Headers

import ApiVersion._

import org.specs2._

class VersionRouterSpec extends mutable.Specification {

  val map: PartialFunction[ApiVersion,String] = {
    case `1.1` => "A"
    case `1.2` => "B"
  }

  case class FakeHeaders(headers: Map[String, Seq[String]], val data: Seq[(String, Seq[String])] = Seq.empty) extends Headers {
    override def getAll(key: String) = headers(key)
    override def keys = headers.keys.toSet
  }

  "version router" should {
    "route to correct version" in {
      val heads = FakeHeaders(Map("Accept" -> List("application/com.tumblr.collins;version=1.2", "foo", "com.tumblr.collins")))
      VersionRouter.route(heads)(map) must_== "B"

    }
    "default route on missing header" in {      
      VersionRouter.route(FakeHeaders(Map[String, Seq[String]]()))(map) must_== map(ApiVersion.defaultVersion)
    }
    "default route on malformed header" in {
      VersionRouter.route(FakeHeaders(Map("Accept" -> List("HASFIAFSHAF"))))(map) must_== map(ApiVersion.defaultVersion)
    }
    "throw exception on invalid version" in {
      val heads = FakeHeaders(Map("Accept" -> List("application/com.tumblr.collins;version=26.12", "foo", "com.tumblr.collins")))
      VersionRouter.route(heads)(map) must throwA[VersionException]
    }
  }

}
