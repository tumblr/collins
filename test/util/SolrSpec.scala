package util.plugins
package solr

import Solr._
import org.specs2._
import models.{Asset, AssetFinder, AssetType, AssetMeta, IpAddresses, State, Status, AssetMetaValue}
import test.ApplicationSpecification
import util.solr.SolrConfig
import util.views.Formatter

class SolrSpec extends ApplicationSpecification {

  import AssetMeta.ValueType._
  import AssetMeta.ValueType

  args(sequential = true)

  "FlatSerializer" should {
    "serialize an asset" in {
      val assetTag = "solr%d".format(scala.util.Random.nextInt)
      val assetType = AssetType.Enum.ServerNode
      val status = Status.Enum.Allocated
      val state = State.Running.get
      val meta = List(
        ("A",String, 0,"a"),
        ("B",String, 0,"b"),
        ("A",String, 1,"a1"),
        ("int", Integer, 0, "1135"),
        ("double", Double, 0, "3.1415"),
        ("bool", Boolean, 0, "false")
      )
      val asset = generateAsset(assetTag, assetType, status, meta, state)
      val addresses = IpAddresses.createForAsset(asset, 2, Some("DEV"))
      val almostExpected = Map(
        SolrKey("TAG", String, false) -> SolrStringValue(assetTag),
        SolrKey("STATUS", Integer, false) -> SolrIntValue(status.id),
        SolrKey("STATE", Integer, false) -> SolrIntValue(state.id),
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

  def generateAsset(tag: String, assetType: AssetType.Enum, status: Status.Enum, metaValues: Seq[(String, ValueType, Int, String)], state: State) = {
    val asset = Asset.create(Asset(tag, status, assetType))
    Asset.setState(asset, state)
    metaValues.foreach{case (name, value_type, group_id, value) =>
      AssetMeta.findOrCreateFromName(name, value_type)
      val meta = AssetMeta.findByName(name).get
      try {
        AssetMetaValue.create(AssetMetaValue(asset.id, meta.id, group_id, value))
      } catch {
        case e: RuntimeException =>
          Thread.sleep(1000)
          AssetMetaValue.create(AssetMetaValue(asset.id, meta.id, group_id, value))
      }
    }
    Asset.findById(asset.id).get
  }

}

class SolrQuerySpec extends ApplicationSpecification {

  def P = new CollinsQueryParser

  import CollinsQueryDSL._
  import AssetMeta.ValueType._


  "CollinsQueryDSL" should {
    "key vals" in {
      "int" in {
        (("foosolr" -> 3): SolrKeyVal) must_== SolrKeyVal("foosolr", SolrIntValue(3))
      }
      "bool" in {
        (("foosolr" -> false): SolrKeyVal) must_== SolrKeyVal("foosolr", SolrBooleanValue(false))
      }
      "double" in {
        (("foosolr" -> 3.1415): SolrKeyVal) must_== SolrKeyVal("foosolr", SolrDoubleValue(3.1415))
      }
      "string" in {
        (("foosolr" -> "bar"): SolrKeyVal) must_== SolrKeyVal("foosolr", SolrStringValue("bar"))
      }
    }

  }

  "CollinsQueryParser" should {
    "key-value" in {
      "string value" in {
        """foosolr = "bar"""".query must_== (("foosolr" -> "bar"): SolrKeyVal)
      }
      "int value" in {
        """foosolr = 3""".query must_== (("foosolr" -> 3): SolrKeyVal)
      }
      "double value" in {
        """foosolr = 3.1415""".query must_== (("foosolr" -> 3.1415): SolrKeyVal)
      }
      "boolean value" in {
        """foosolr = false""".query must_== (("foosolr" -> false): SolrKeyVal)
      }
      "range both" in {
        """foosolr = [3, 5]""".query must_== SolrKeyRange("foosolr", Some(SolrIntValue(3)), Some(SolrIntValue(5)))
      }
      "range opt low" in {
        """foosolr = [*, 5]""".query must_== SolrKeyRange("foosolr", None, Some(SolrIntValue(5)))
      }
      "range opt high" in {
        """foosolr = [3, *]""".query must_== SolrKeyRange("foosolr", Some(SolrIntValue(3)), None)
      }
      "range opt both" in {
        """foosolr = [*, *]""".query must_== SolrKeyRange("foosolr", None, None)
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
        """foosolr = 3 AND bar = 4""".query must_== (("foosolr" -> 3) AND ("bar" -> 4))
      }
      "simple OR" in {
        """foosolr = 3 OR bar = 4""".query must_== (("foosolr" -> 3) OR ("bar" -> 4))
      }
      "case insensitive AND" in {
        """foosolr = 3 and bar = 4""".query must_== """foosolr = 3 AND bar = 4""".query
      }
      "case insensitive OR" in {
        """foosolr = 3 or bar = 4""".query must_== """foosolr = 3 OR bar = 4""".query
      }
      "order of operations" in {
        """foosolr = 4 OR bar = 4 AND baz = false""".query must_== (("foosolr" -> 4) OR ("bar" -> 4 AND "baz" -> false))
      }
      "arbitrary parentheses" in {
        """(((((((foosolr = true)))))))""".query must_== SolrKeyVal("foosolr", SolrBooleanValue(true))
      }
      "simple NOT" in {
        """NOT foosolr = 5""".query must_== CollinsQueryDSL.not("foosolr" -> 5)
      }
      "case insensitive NOT" in {
        """not foosolr = 5""".query must_== """NOT foosolr = 5""".query
      }
      "not OOO" in {
        """NOT foosolr = 5 OR bar = false""".query must_== (SolrNotOp(("foosolr" -> 5)) OR ("bar" -> false))
      }
      "negate complex expression" in {
        """NOT (foosolr = 5 AND bar = "baz")""".query must_== SolrNotOp(("foosolr" -> 5) AND ("bar" -> "baz"))
      }
        
    }
  }

  "CQL abstract syntax-tree" should {

    "solr query generation" in {
      "simple keyval" in {
        "foosolr = 3".query.toSolrQueryString must_== "foosolr:3"
      }
      "wildcard" in {
        "foosolr = bar*".query.toSolrQueryString must_== "foosolr:bar*"
      }
      "ANDs" in {
         """foosolr = 3 AND bar = "abcdef" AND baz = true""".query.toSolrQueryString must_== "foosolr:3 AND bar:abcdef AND baz:true"
      }
      "ORs" in {
         """foosolr = 3 OR bar = "abcdef" OR baz = true""".query.toSolrQueryString must_== "foosolr:3 OR bar:abcdef OR baz:true"
      }
      "NOT" in {
        """NOT foosolr = 3""".query.toSolrQueryString must_== "NOT foosolr:3"
      }
      "nested exprs" in {
        """(foosolr = 3 OR foosolr = 4) AND (bar = true OR (bar = false AND baz = 5))""".query.toSolrQueryString must_== "(foosolr:3 OR foosolr:4) AND (bar:true OR (bar:false AND baz:5))"
      }
      "support unquoted one-word strings" in {
        """foosolr = bar""".query must_== """foosolr = "bar"""".query
      }
      "properly escape leading dash" in {
        """TAG = -""".query.toSolrQueryString must_== """TAG:\-"""
      }
    }

    "type checking" in {
      "keyvals" in {
        val m = AssetMeta.findOrCreateFromName("foosolr", Integer)
        "foosolr = 3".query.typeCheck must_== Right("FOOSOLR_meta_i = 3".query)
        "foosolr = 3.123".query.typeCheck must beAnInstanceOf[Left[String, SolrExpression]]
        "foosolr = true".query.typeCheck must beAnInstanceOf[Left[String, SolrExpression]]
        """foosolr = "3"""".query.typeCheck must beAnInstanceOf[Left[String, SolrExpression]]
      }
      "case insensitive key" in {
        "FoOsOlR = 3".query.typeCheck must_== Right("FOOSOLR_meta_i = 3".query)
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
        """type = "FOOSOLRBARRRRRR"""".query.typeCheck must beAnInstanceOf[Left[String, SolrExpression]]
      }
      "use enum id" in {
        """type = 1""".query.typeCheck must_== "TYPE = SERVER_NODE".query.typeCheck
      }
      "generated key" in {
        """num_disks = 3""".query.typeCheck must_== Right(SolrKeyVal("NUM_DISKS_meta_i", SolrIntValue(3)))
      }
      "AND" in {
        "foosolr = 3 AND foosolr = false".query.typeCheck must beAnInstanceOf[Left[String, SolrExpression]]
      }
      "OR" in {
        "foosolr = 3 OR foosolr = false".query.typeCheck must beAnInstanceOf[Left[String, SolrExpression]]
      }
      "range" in {
        "foosolr = [3, 5]".query.typeCheck must beAnInstanceOf[Right[String, SolrExpression]]
        "foosolr = [false, 5]".query.typeCheck must beAnInstanceOf[Left[String, SolrExpression]]
        "foosolr = [3, false]".query.typeCheck must beAnInstanceOf[Left[String, SolrExpression]]
      }
      "not lose NOT" in {
        "NOT foosolr = 3".query.typeCheck must_== Right(SolrNotOp(SolrKeyVal("FOOSOLR_meta_i", SolrIntValue(3))))
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
        Some("foosolrtag"), 
        Some(Status.Enum.Allocated), 
        Some(AssetType.Enum.ServerNode),
        Some(somedate),
        Some(somedate),
        Some(somedate),
        Some(somedate),
        Some(State.Running.get)
      )
      val expected = List(
        SolrKeyVal("tag", SolrStringValue("foosolrtag")),
        SolrKeyVal("status", SolrIntValue(Status.Enum.Allocated.id)),
        SolrKeyVal("assetType", SolrIntValue(AssetType.Enum.ServerNode.id)),
        SolrKeyRange("created", Some(SolrStringValue(dateString)),Some(SolrStringValue(dateString))),
        SolrKeyRange("updated", Some(SolrStringValue(dateString)),Some(SolrStringValue(dateString))),
        SolrKeyVal("state", SolrIntValue(State.Running.get.id))
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
        None,
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

  def home = SolrConfig.embeddedSolrHome

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
