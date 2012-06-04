
package models

import test.ApplicationSpecification

import org.specs2._
import specification._
import play.api.libs.json._

class AssetSearchParametersSpec extends mutable.Specification {

  //def <<(s: String) = Json.parse

  val EMPTY_RESULT_TUPLE = (Nil, Nil, Nil)
  val EMPTY_FINDER = AssetFinder(None, None, None, None, None, None, None)

  "AssetSearchParameters" should {
    
    "generate correct query string" in {

      "asks for details" in {
        AssetSearchParameters(EMPTY_RESULT_TUPLE, EMPTY_FINDER, None).toQueryString.get("details") must_== Some("true")
      }
      "include operation with empty params" in {
        AssetSearchParameters(EMPTY_RESULT_TUPLE, EMPTY_FINDER, Some("and")).toQueryString.get("operation") must_== Some("and")
      }
      "include operation with non-empty parms" in {
        AssetSearchParameters(EMPTY_RESULT_TUPLE, EMPTY_FINDER.copy(tag = Some("foo")), Some("and")).toQueryString.get("operation") must_== Some("and")
      }
      "IPMI info" in {
        AssetSearchParameters(
          EMPTY_RESULT_TUPLE.copy(_1 = List((IpmiInfo.Enum.IpmiAddress, "1.2.3.4"))),
          EMPTY_FINDER, 
          None
        ).toQueryString.get(IpmiInfo.Enum.IpmiAddress.toString) must_== Some("1.2.3.4")
      }
      "single attribute" in {
        AssetSearchParameters(
          EMPTY_RESULT_TUPLE.copy(_2 = List((AssetMeta("CPU_COUNT", -1, "","", 0L), 2.toString))),
          EMPTY_FINDER,
          None
        ).toQueryString.get("attribute") must_== Some("CPU_COUNT;2")
      }
      "multiple attributes" in {
        //TODO: what's the syntax for this?
      }

      //TODO: Verify if multiple ip-address searching is supported, or why is ip address a seq?
      "ip address" in {
        AssetSearchParameters(
          EMPTY_RESULT_TUPLE.copy(_3 = List("1.3.5.7")),
          EMPTY_FINDER,
          None
        ).toQueryString.get("ip_address") must_== Some("1.3.5.7")
      }


    }
  }
}
        
