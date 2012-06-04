
package models

import test.ApplicationSpecification

import org.specs2._
import specification._
import play.api.libs.json._

class AssetSearchParametersSpec extends mutable.Specification {

  val EMPTY_RESULT_TUPLE = (Nil, Nil, Nil)
  val EMPTY_FINDER = AssetFinder(None, None, None, None, None, None, None)


  class QuerySeq(items: Seq[(String, String)]) {
    def findByKey(key: String): List[String] = items.filter(_._1 == key).map{_._2}.toList

    /**
     * returns the value for key iff there is exactly one value for key (aka
     * don't allow multiple values for the key)
     */
    def findOne(key: String) = findByKey(key) match {
      case head :: tail if (tail.size > 0) => None
      case List(one) => Some(one)
      case _ => None
    }
  }

  implicit def seq2queryseq(seq: Seq[(String, String)]): QuerySeq = new QuerySeq(seq)

  "QuerySeq" should {
    "find by key" in {
      List("A" -> "1", "B" -> "2", "A" -> "3").findByKey("A") must_== List("1", "3")
    }
    "find one" in {
      "one value returns some" in {
        List("A" -> "1", "B" -> "2").findOne("A") must_== Some("1")
      }
      "zero values returns None" in {
        List("X" -> "1", "B" -> "2").findOne("A") must_== None
      }
      ">1 values returns None" in {
        List("A" -> "1", "B" -> "2", "A" -> "3").findOne("A") must_== None
      }
    }
  }

  "AssetSearchParameters" should {
    
    "generate correct query string sequence" in {

      "asks for details" in {
        AssetSearchParameters(EMPTY_RESULT_TUPLE, EMPTY_FINDER, None).toSeq.findOne("details") must_== Some("true")
      }
      
      "include operation with empty params" in {
        AssetSearchParameters(EMPTY_RESULT_TUPLE, EMPTY_FINDER, Some("and")).toSeq.findOne("operation") must_== Some("and")
      }
      
      "include operation with non-empty parms" in {
        AssetSearchParameters(EMPTY_RESULT_TUPLE, EMPTY_FINDER.copy(tag = Some("foo")), Some("and")).toSeq.findOne("operation") must_== Some("and")
      }
      
      "IPMI info" in {
        AssetSearchParameters(
          EMPTY_RESULT_TUPLE.copy(_1 = List((IpmiInfo.Enum.IpmiAddress, "1.2.3.4"))),
          EMPTY_FINDER, 
          None
        ).toSeq.findOne(IpmiInfo.Enum.IpmiAddress.toString) must_== Some("1.2.3.4")
      }
      
      "single attribute" in {
        AssetSearchParameters(
          EMPTY_RESULT_TUPLE.copy(_2 = List((AssetMeta("CPU_COUNT", -1, "","", 0L), 2.toString))),
          EMPTY_FINDER,
          None
        ).toSeq.findOne("attribute") must_== Some("CPU_COUNT;2")
      }

      "multiple attributes" in {
        val metas = List(
          (AssetMeta("CPU_COUNT", -1, "","", 0L), 2.toString), 
          (AssetMeta("FOO", -1, "", "", 0L), "BAR")
        )
        AssetSearchParameters(
          EMPTY_RESULT_TUPLE.copy(_2 = metas),
          EMPTY_FINDER,
          None
        ).toSeq.findByKey("attribute") must_== List("CPU_COUNT;2", "FOO;BAR")
      }

      "ip address" in {
        AssetSearchParameters(
          EMPTY_RESULT_TUPLE.copy(_3 = List("1.3.5.7")),
          EMPTY_FINDER,
          None
        ).toSeq.findOne("ip_address") must_== Some("1.3.5.7")
      }

    }
  }
}
        
