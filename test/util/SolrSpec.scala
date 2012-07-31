package util.plugins
package solr

import Solr._
import org.specs2._
import models.{Asset, AssetFinder, AssetType, AssetMeta, IpAddresses, Status, AssetMetaValue}
import test.ApplicationSpecification
import util.views.Formatter
import util.Config

class SolrSpec extends ApplicationSpecification {

  import AssetMeta.ValueType._
  import AssetMeta.ValueType

  "FlatSerializer" should {
    "serialize an asset" in {
      val assetTag = "solr1"
      val assetType = AssetType.Enum.ServerNode
      val status = Status.Enum.Allocated
      val meta = List(
        ("A",String, 0,"a"),
        ("B",String, 0,"b"),
        ("A",String, 1,"a1"),
        ("int", Integer, 0, "1135"),
        ("double", Double, 0, "3.1415"),
        ("bool", Boolean, 0, "false")
      )
      val asset = generateAsset(assetTag, assetType, status, meta)
      val addresses = IpAddresses.createForAsset(asset, 2, Some("DEV"))
      val almostExpected = Map(
        SolrKey("TAG", String, false) -> SolrStringValue(assetTag),
        SolrKey("STATUS", Integer, false) -> SolrIntValue(status.id),
        SolrKey("TYPE", Integer, false) -> SolrIntValue(assetType.id),
        SolrKey("CREATED", String, false) -> SolrStringValue(Formatter.solrDateFormat(asset.created)),
        SolrKey("A", String, true) -> SolrMultiValue(SolrStringValue("a") :: SolrStringValue("a1") :: Nil),
        SolrKey("B", String, true) -> SolrStringValue("b"),
        SolrKey("INT", Integer, true) -> SolrIntValue(1135),
        SolrKey("DOUBLE", Double, true) -> SolrDoubleValue(3.1415),
        SolrKey("BOOL", Boolean, true) -> SolrBooleanValue(false),
        SolrKey("IP_ADDRESS", String, false) -> SolrMultiValue(addresses.map{a => SolrStringValue(a.dottedAddress)})
      )
      val expected = almostExpected + (SolrKey("KEYS", String, true) -> SolrMultiValue(almostExpected.map{case(k,v) => SolrStringValue(k.name)}.toSeq, String))
      //expected.foreach{e => println(e.toString)}
      //println("---")
      val actual = (new FlatSerializer).serialize(asset) 
      //actual.foreach{a => println(a.toString)}
      actual must_== expected
    }
    "post-process number of disks" in {
      val m = Map[SolrKey, SolrValue](SolrKey("DISK_SIZE_BYTES", String, true) -> SolrMultiValue(SolrStringValue("123") :: SolrStringValue("456") :: Nil))
      val expected = m + 
        (SolrKey("NUM_DISKS", Integer, true) -> SolrIntValue(2)) + 
        (SolrKey("KEYS", String, true) -> SolrMultiValue(SolrStringValue("DISK_SIZE_BYTES") :: SolrStringValue("NUM_DISKS") :: Nil))
      (new FlatSerializer).postProcess(m) must_== expected
    }
  }

  def generateAsset(tag: String, assetType: AssetType.Enum, status: Status.Enum, metaValues: Seq[(String, ValueType, Int, String)]) = {
    val asset = Asset.create(Asset(tag, status, assetType))
    metaValues.foreach{case (name, value_type, group_id, value) =>
      val meta = AssetMeta.findOrCreateFromName(name, value_type)
      AssetMetaValue.create(AssetMetaValue(asset.id, meta.id, group_id, value))
    }
    asset
  }

}

class SolrQuerySpec extends ApplicationSpecification {

  def P = new CollinsQueryParser

  import CollinsQueryDSL._
  import AssetMeta.ValueType._

  "CollinsQueryDSL" should {
    "key vals" in {
      "int" in {
        (("foo" -> 3): SolrKeyVal) must_== SolrKeyVal("foo", SolrIntValue(3))
      }
      "bool" in {
        (("foo" -> false): SolrKeyVal) must_== SolrKeyVal("foo", SolrBooleanValue(false))
      }
      "double" in {
        (("foo" -> 3.1415): SolrKeyVal) must_== SolrKeyVal("foo", SolrDoubleValue(3.1415))
      }
      "string" in {
        (("foo" -> "bar"): SolrKeyVal) must_== SolrKeyVal("foo", SolrStringValue("bar"))
      }
    }

  }

  "CollinsQueryParser" should {
    "key-value" in {
      "string value" in {
        """foo = "bar"""".query must_== (("foo" -> "bar"): SolrKeyVal)
      }
      "int value" in {
        """foo = 3""".query must_== (("foo" -> 3): SolrKeyVal)
      }
      "double value" in {
        """foo = 3.1415""".query must_== (("foo" -> 3.1415): SolrKeyVal)
      }
      "boolean value" in {
        """foo = false""".query must_== (("foo" -> false): SolrKeyVal)
      }
      "range both" in {
        """foo = [3, 5]""".query must_== SolrKeyRange("foo", Some(SolrIntValue(3)), Some(SolrIntValue(5)))
      }
      "range opt low" in {
        """foo = [*, 5]""".query must_== SolrKeyRange("foo", None, Some(SolrIntValue(5)))
      }
      "range opt high" in {
        """foo = [3, *]""".query must_== SolrKeyRange("foo", Some(SolrIntValue(3)), None)
      }
      "range opt both" in {
        """foo = [*, *]""".query must_== SolrKeyRange("foo", None, None)
      }
      "ip address" in {
        """ip_address = "192.168.1.1"""".query must_== SolrKeyVal("ip_address", SolrStringValue("192.168.1.1"))
      }
      "unquoted ip address" in {
        """ip_address = 192.168.1.1""".query must_== SolrKeyVal("ip_address", SolrStringValue("192.168.1.1"))
        """ip_address = 192.168.1.*""".query must_== SolrKeyVal("ip_address", SolrStringValue("192.168.1.*"))
        """ip_address = 192.168.*""".query must_== SolrKeyVal("ip_address", SolrStringValue("192.168.*"))
        """ip_address = 192.*""".query must_== SolrKeyVal("ip_address", SolrStringValue("192.*"))
        """ip_address = *""".query must_== SolrKeyVal("ip_address", SolrStringValue("*"))
      }
    }

    "complex expressions" in {
      "simple AND" in {
        """foo = 3 AND bar = 4""".query must_== (("foo" -> 3) AND ("bar" -> 4))
      }
      "simple OR" in {
        """foo = 3 OR bar = 4""".query must_== (("foo" -> 3) OR ("bar" -> 4))
      }
      "case insensitive AND" in {
        """foo = 3 and bar = 4""".query must_== """foo = 3 AND bar = 4""".query
      }
      "case insensitive OR" in {
        """foo = 3 or bar = 4""".query must_== """foo = 3 OR bar = 4""".query
      }
      "order of operations" in {
        """foo = 4 OR bar = 4 AND baz = false""".query must_== (("foo" -> 4) OR ("bar" -> 4 AND "baz" -> false))
      }
      "arbitrary parentheses" in {
        """(((((((foo = true)))))))""".query must_== SolrKeyVal("foo", SolrBooleanValue(true))
      }
      "simple NOT" in {
        """NOT foo = 5""".query must_== CollinsQueryDSL.not("foo" -> 5)
      }
      "case insensitive NOT" in {
        """not foo = 5""".query must_== """NOT foo = 5""".query
      }
      "not OOO" in {
        """NOT foo = 5 OR bar = false""".query must_== (SolrNotOp(("foo" -> 5)) OR ("bar" -> false))
      }
      "negate complex expression" in {
        """NOT (foo = 5 AND bar = "baz")""".query must_== SolrNotOp(("foo" -> 5) AND ("bar" -> "baz"))
      }
        
    }
  }

  "CQL abstract syntax-tree" should {

    "solr query generation" in {
      "simple keyval" in {
        "foo = 3".query.toSolrQueryString must_== "foo:3"
      }
      "wildcard" in {
        "foo = bar*".query.toSolrQueryString must_== "foo:bar*"
      }
      "ANDs" in {
         """foo = 3 AND bar = "abcdef" AND baz = true""".query.toSolrQueryString must_== "foo:3 AND bar:abcdef AND baz:true"
      }
      "ORs" in {
         """foo = 3 OR bar = "abcdef" OR baz = true""".query.toSolrQueryString must_== "foo:3 OR bar:abcdef OR baz:true"
      }
      "NOT" in {
        """NOT foo = 3""".query.toSolrQueryString must_== "NOT foo:3"
      }
      "nested exprs" in {
        """(foo = 3 OR foo = 4) AND (bar = true OR (bar = false AND baz = 5))""".query.toSolrQueryString must_== "(foo:3 OR foo:4) AND (bar:true OR (bar:false AND baz:5))"
      }
      "support unquoted one-word strings" in {
        """foo = bar""".query must_== """foo = "bar"""".query
      }
    }

    "type checking" in {
      "keyvals" in {
        val m = AssetMeta.findOrCreateFromName("foo", Integer)
        "foo = 3".query.typeCheck must_== Right("FOO_meta_i = 3".query)
        "foo = 3.123".query.typeCheck must beAnInstanceOf[Left[String, SolrExpression]]
        "foo = true".query.typeCheck must beAnInstanceOf[Left[String, SolrExpression]]
        """foo = "3"""".query.typeCheck must beAnInstanceOf[Left[String, SolrExpression]]
      }
      "case insensitive key" in {
        "FoO = 3".query.typeCheck must_== Right("FOO_meta_i = 3".query)
      }
      "valid enum" in {
        """type = "SERVER_NODE"""".query.typeCheck must_== Right("TYPE = 1".query)
      }
      "case insensitive status enum" in {
        """status = unallocated""".query.typeCheck must_== "STATUS = Unallocated".query.typeCheck
      }
      "case insensitive type enum" in {
        """type = server_node""".query.typeCheck must_== """TYPE = SERVER_NODE""".query.typeCheck
      }
      "invalid enum" in {
        """type = "FOOBARRRRRR"""".query.typeCheck must beAnInstanceOf[Left[String, SolrExpression]]
      }
      "use enum id" in {
        """type = 1""".query.typeCheck must_== "TYPE = SERVER_NODE".query.typeCheck
      }
      "generated key" in {
        """num_disks = 3""".query.typeCheck must_== Right(SolrKeyVal("NUM_DISKS_meta_i", SolrIntValue(3)))
      }
      "AND" in {
        "foo = 3 AND foo = false".query.typeCheck must beAnInstanceOf[Left[String, SolrExpression]]
      }
      "OR" in {
        "foo = 3 OR foo = false".query.typeCheck must beAnInstanceOf[Left[String, SolrExpression]]
      }
      "range" in {
        "foo = [3, 5]".query.typeCheck must beAnInstanceOf[Right[String, SolrExpression]]
        "foo = [false, 5]".query.typeCheck must beAnInstanceOf[Left[String, SolrExpression]]
        "foo = [3, false]".query.typeCheck must beAnInstanceOf[Left[String, SolrExpression]]
      }
      "not lose NOT" in {
        "NOT foo = 3".query.typeCheck must_== Right(SolrNotOp(SolrKeyVal("FOO_meta_i", SolrIntValue(3))))
      }
      "tag search" in {
        """tag = test""".query.typeCheck must_== Right(SolrKeyVal("TAG", SolrStringValue("test")))
      }
    }


  }
  
  "AssetFinder solr conversion" should {
    "basic conversion" in {
      val somedate = new java.util.Date
      val dateString = util.views.Formatter.dateFormat(somedate)
      val afinder = AssetFinder(
        Some("footag"), 
        Some(Status.Enum.Allocated), 
        Some(AssetType.Enum.ServerNode),
        Some(somedate),
        Some(somedate),
        Some(somedate),
        Some(somedate)
      )
      val expected = List(
        SolrKeyVal("tag", SolrStringValue("footag")),
        SolrKeyVal("status", SolrIntValue(Status.Enum.Allocated.id)),
        SolrKeyVal("assetType", SolrIntValue(AssetType.Enum.ServerNode.id)),
        SolrKeyRange("created", Some(SolrStringValue(dateString)),Some(SolrStringValue(dateString))),
        SolrKeyRange("updated", Some(SolrStringValue(dateString)),Some(SolrStringValue(dateString)))
      )
      afinder.toSolrKeyVals.toSet must_== expected.toSet

    }
    "open date ranges" in {
      val somedate = new java.util.Date
      val dateString = util.views.Formatter.dateFormat(somedate)
      val afinder = AssetFinder(
        None,
        None,
        None,
        None,
        Some(somedate),
        Some(somedate),
        None
      )
      val expected = List(
        SolrKeyRange("created", Some(SolrStringValue(dateString)),None),
        SolrKeyRange("updated",None,Some(SolrStringValue(dateString)))
      )
      afinder.toSolrKeyVals.toSet must_== expected.toSet

    }

  }
        

}

class SolrServerSpecification extends ApplicationSpecification {

  def home = Config.getString("solr.embeddedSolrHome", "NONE") 

  lazy val server = Solr.getNewEmbeddedServer(home)

  "solr server" should {
    "get solrhome config" in {
      home mustNotEqual "NONE"
    }

    "launch embedded server without crashing" in {
      val s = server
      true must_== true
    }
  }

}
