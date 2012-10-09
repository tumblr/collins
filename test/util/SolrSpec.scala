package collins.solr

import Solr._
import org.specs2._
import models.{Asset, AssetFinder, AssetType, AssetMeta, AssetSearchParameters, IpAddresses, IpmiInfo, State, Status, AssetMetaValue}
import test.ApplicationSpecification
import util.views.Formatter

class SolrSpec extends ApplicationSpecification {

  import AssetMeta.ValueType._
  import AssetMeta.ValueType

  args(sequential = true)

  "FlatSerializer" should {
    "serialize an asset" in {
      val assetTag = "solr%d".format(scala.util.Random.nextInt)
      val assetType = AssetType.ServerNode.get
      val status = Status.Allocated.get
      val state = State.Running.get
      val meta = List(
        ("A",String, 0,"a"),
        ("B",String, 0,"b"),
        ("A",String, 1,"a1"),
        ("int", Integer, 0, "1135"),
        ("double", Double, 0, "3.1415"),
        ("bool", Boolean, 0, "false"),
        ("HOSTNAME", String, 0, "my_hostname")
      )
      val asset = generateAsset(assetTag, assetType, status, meta, state)
      val addresses = IpAddresses.createForAsset(asset, 2, Some("DEV"))
      val ipmi = IpmiInfo.createForAsset(asset)
      val almostExpected = Map(
        SolrKey("ID", Integer, false, false) -> SolrIntValue(asset.id.toInt),
        SolrKey("TAG", String, false, false) -> SolrStringValue(assetTag, StrictUnquoted),
        SolrKey("STATUS", String, false, false) -> SolrStringValue(status.name, StrictUnquoted),
        SolrKey("STATE", String, false, false) -> SolrStringValue(state.name, StrictUnquoted),
        SolrKey("TYPE", String, false, false) -> SolrStringValue(assetType.name, StrictUnquoted),
        SolrKey("CREATED", String, false, false) -> SolrStringValue(Formatter.solrDateFormat(asset.created), StrictUnquoted),
        SolrKey("A", String, true, true) -> SolrMultiValue(Set(SolrStringValue("a", StrictUnquoted), SolrStringValue("a1", StrictUnquoted))),
        SolrKey("B", String, true, true) -> SolrStringValue("b", StrictUnquoted),
        SolrKey("INT", Integer, true, true) -> SolrIntValue(1135),
        SolrKey("DOUBLE", Double, true, true) -> SolrDoubleValue(3.1415),
        SolrKey("BOOL", Boolean, true, true) -> SolrBooleanValue(false),
        SolrKey("IP_ADDRESS", String, false, true) -> SolrMultiValue(addresses.map{a => SolrStringValue(a.dottedAddress, StrictUnquoted)}.toSet),
        SolrKey("HOSTNAME", String, false, false) -> SolrStringValue("my_hostname", StrictUnquoted),
        SolrKey("IPMI_ADDRESS", String, true, false) -> SolrStringValue(ipmi.dottedAddress, StrictUnquoted)
      )

      val sortKeys = Map(
        SolrKey("ID_SORT", String, false) -> SolrStringValue(asset.id.toString, StrictUnquoted),
        SolrKey("TAG_SORT", String, false) -> SolrStringValue(assetTag, StrictUnquoted),
        SolrKey("STATUS_SORT", String, false) -> SolrStringValue(status.name, StrictUnquoted),
        SolrKey("STATE_SORT", String, false) -> SolrStringValue(state.name, StrictUnquoted),
        SolrKey("TYPE_SORT", String, false) -> SolrStringValue(assetType.name, StrictUnquoted),
        SolrKey("CREATED_SORT", String, false) -> SolrStringValue(Formatter.solrDateFormat(asset.created), StrictUnquoted),
        SolrKey("HOSTNAME_SORT", String, false) -> SolrStringValue("my_hostname", StrictUnquoted),
        SolrKey("IPMI_ADDRESS_SORT", String, false) -> SolrStringValue(ipmi.dottedAddress, StrictUnquoted)
      )

      val expected = almostExpected 
        .++(sortKeys)
        .+((SolrKey("KEYS", String, true) -> SolrMultiValue(almostExpected.map{case(k,v) => SolrStringValue(k.name, StrictUnquoted)}.toSet, String)))
      val actual = (new FlatSerializer).serialize(asset) 
      val actualSet: Set[(SolrKey, SolrValue)] = actual.toSet
      val expectedSet: Set[(SolrKey, SolrValue)] = expected.toSet
      /*
      expected.foreach{e => println(e.toString)}
      println("---")
      actual.foreach{a => println(a.toString)}
      println("---")
      (expectedSet diff actualSet).foreach{e => println(e.toString)}
      println("---")
      (actualSet diff expectedSet).foreach{e => println(e.toString)}
      */
      actualSet must_== expectedSet
    }
    "post-process number of disks" in {
      val m = Map[SolrKey, SolrValue](SolrKey("DISK_SIZE_BYTES", String, true) -> SolrMultiValue(Set(SolrStringValue("123", StrictUnquoted), SolrStringValue("456", StrictUnquoted))))
      val expected = m + 
        (SolrKey("NUM_DISKS", Integer, true) -> SolrIntValue(2)) + 
        (SolrKey("KEYS", String, true) -> SolrMultiValue(Set(SolrStringValue("DISK_SIZE_BYTES", StrictUnquoted), SolrStringValue("NUM_DISKS", StrictUnquoted))))
      (new FlatSerializer).postProcess(m) must_== expected
    }
  }

  def generateAsset(tag: String, assetType: AssetType, status: Status, metaValues: Seq[(String, ValueType, Int, String)], state: State) = {
    val asset = Asset.create(Asset(tag, status, assetType))
    Asset.partialUpdate(asset, None, None, Some(state))
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
      "quoted string" in {
        (("foosolr" -> "bar".quoted): SolrKeyVal) must_== SolrKeyVal("foosolr", SolrStringValue("bar", Quoted))
      }
    }

  }

  "CollinsQueryParser" should {
    "empty query" in {
      "*".query must_== EmptySolrQuery
    }
    "key-value" in {
      "string value" in {
        """foosolr = "bar"""".query must_== (("foosolr" -> "bar".quoted): SolrKeyVal)
      }
      "int value" in {
        """foosolr = 3""".query must_== (("foosolr" -> "3"): SolrKeyVal)
      }
      "double value" in {
        """foosolr = 3.1415""".query must_== (("foosolr" -> "3.1415"): SolrKeyVal)
      }
      "boolean value" in {
        """foosolr = false""".query must_== (("foosolr" -> "false"): SolrKeyVal)
      }
      "leading regex wildcard" in {
        """foosolr = .*bar""".query must_== SolrKeyVal("foosolr", SolrStringValue("bar", LWildcard))
      }
      "number-start string value" in {
        """foosolr = 03abc.xyz09-wer:10""".query must_== SolrKeyVal("foosolr", SolrStringValue("03abc.xyz09-wer:10", Unquoted))
      }
      "unquoted mac address" in {
        """foosolr = 04:7d:7b:06:8f:f9""".query must_== SolrKeyVal("foosolr", SolrStringValue("04:7d:7b:06:8f:f9", Unquoted))
      }
      "range both" in {
        """foosolr = [3, 5]""".query must_== SolrKeyRange("foosolr", Some(SolrStringValue("3", StrictUnquoted)), Some(SolrStringValue("5", StrictUnquoted)))
      }
      "range opt low" in {
        """foosolr = [*, 5]""".query must_== SolrKeyRange("foosolr", None, Some(SolrStringValue("5", StrictUnquoted)))
      }
      "range opt high" in {
        """foosolr = [3, *]""".query must_== SolrKeyRange("foosolr", Some(SolrStringValue("3", StrictUnquoted)), None)
      }
      "range opt both" in {
        """foosolr = [*, *]""".query must_== SolrKeyRange("foosolr", None, None)
      }
      "ip address" in {
        """ip_address = "192.168.1.1"""".query must_== SolrKeyVal("ip_address", SolrStringValue("192.168.1.1", Quoted))
      }
      "unquoted ip address" in {
        """ip_address = 192.168.1.1""".query must_== SolrKeyVal("ip_address", SolrStringValue("192.168.1.1", Unquoted))
        """ip_address = 192.168.1.*""".query must_== SolrKeyVal("ip_address", SolrStringValue("192.168.1", RWildcard))
        """ip_address = 192.168.*""".query must_== SolrKeyVal("ip_address", SolrStringValue("192.168", RWildcard))
        """ip_address = 192.*""".query must_== SolrKeyVal("ip_address", SolrStringValue("192", RWildcard))
        """ip_address = *""".query must_== SolrKeyVal("ip_address", SolrStringValue("*", FullWildcard))
      }
    }

    "complex expressions" in {
      "simple AND" in {
        """foosolr = 3 AND bar = 4""".query must_== (("foosolr" -> "3") AND ("bar" -> "4"))
      }
      "simple OR" in {
        """foosolr = 3 OR bar = 4""".query must_== (("foosolr" -> "3") OR ("bar" -> "4"))
      }
      "case insensitive AND" in {
        """foosolr = 3 and bar = 4""".query must_== """foosolr = 3 AND bar = 4""".query
      }
      "case insensitive OR" in {
        """foosolr = 3 or bar = 4""".query must_== """foosolr = 3 OR bar = 4""".query
      }
      "order of operations" in {
        """foosolr = 4 OR bar = 4 AND baz = false""".query must_== (("foosolr" -> "4") OR ("bar" -> "4" AND "baz" -> "false"))
      }
      "arbitrary parentheses" in {
        """(((((((foosolr = true)))))))""".query must_== SolrKeyVal("foosolr", SolrStringValue("true", Unquoted))
      }
      "simple NOT" in {
        """NOT foosolr = 5""".query must_== CollinsQueryDSL.not("foosolr" -> "5")
      }
      "case insensitive NOT" in {
        """not foosolr = 5""".query must_== """NOT foosolr = 5""".query
      }
      "not OOO" in {
        """NOT foosolr = 5 OR bar = false""".query must_== (SolrNotOp(("foosolr" -> "5")) OR ("bar" -> "false"))
      }
      "negate complex expression" in {
        """NOT (foosolr = 5 AND bar = "baz")""".query must_== SolrNotOp(("foosolr" -> "5") AND ("bar" -> "baz".quoted))
      }
        
    }
  }

  "StringValueFormat" should {
    def s(str: String) = StringValueFormat.createValueFor(str)
    def S(str: String, format: StringValueFormat) = SolrStringValue(str, format)
    def LR = LRWildcard
    def L = LWildcard
    def R = RWildcard
    def Q = Quoted
    def U = Unquoted
    "handle wildcarding" in {
      "foo" in {
        s("foo") must_== S("foo", U)
      }
      "*foo" in {
        s("*foo") must_== S("foo", L)
      }
      "*foo*"in {
        s("*foo*") must_== S("foo", LR)
      }
      "foo*"in {
        s("foo*") must_== S("foo", R)
      }
      "foo.*"in {
        s("foo.*") must_== S("foo", R)
      }
      "^foo"in {
        s("^foo") must_== S("foo", R)
      }
      "^foo.*"in {
        s("^foo.*") must_== S("foo", R)
      }
      "^foo*"in {
        s("^foo*") must_== S("foo", R)
      }
      "^foo$"in {
        s("^foo$") must_== S("foo", Q)
      }
      "*foo$"in {
        s("*foo$") must_== S("foo", L)
      }
      "foo$"in {
        s("foo$") must_== S("foo", L)
      }
    }

    "handle character escaping" in {
      "quoted" in {
        S("04:7d:7b:06:8f:f9", Q).toSolrQueryString(false) must_== """"04:7d:7b:06:8f:f9""""
      }
      "wildcard" in {
        S("04:7d:7b:06:8",R).toSolrQueryString(false) must_== """04\:7d\:7b\:06\:8*"""
      }
      "strict unquoted" in {
        S("04:7d:7b:06:8f:f9", StrictUnquoted).toSolrQueryString(false) must_== """04\:7d\:7b\:06\:8f\:f9"""
      }
    }
  } 


  "CQL abstract syntax-tree" should {

    "solr query generation" in {
      "empty query" in {
        "*".query.toSolrQueryString must_== "*:*"
      }
      "field wildcard" in {
        "tag = *".query.toSolrQueryString must_== "tag:*"
      }
      "simple keyval" in {
        //the quotes are expected since it hasn't type inferred to an int yet
        "foosolr = 3".query.toSolrQueryString must_== """foosolr:"3""""
      }
      "not pad unquoted unchecked strings with wildcards" in {
        "foo = bar".query.toSolrQueryString must_== """foo:"bar""""
      }
      "handle ^" in {
        "foo = ^bar".query.toSolrQueryString must_== """foo:bar*"""
      }
      "handle $" in {
        "foo = bar$".query.toSolrQueryString must_== """foo:*bar"""
      }
      "handle both ^ and $" in {
        "foo = ^bar$".query.toSolrQueryString must_== """foo:"bar""""
      }
      "not handle ^ or $ in quoted string" in {
        """foo = "^bar$"""".query.toSolrQueryString must_== """foo:"^bar$""""
      }
      "quoted dash" in {
        """tag=-""".query.toSolrQueryString must_== """tag:"-""""
      }
      "leading wildcard" in {
        """hostname=*foo""".query.toSolrQueryString must_== """hostname:*foo"""
      }
      "trailing wildcard" in {
        """hostname=foo*""".query.toSolrQueryString must_== """hostname:foo*"""
      }
      "not quote ranges" in {
        """foo = [abc, abd]""".query.toSolrQueryString must_== """foo:[abc TO abd]"""
      }
      "ANDs" in {
         """foosolr = 3 AND bar = "abcdef" AND baz = true""".query.toSolrQueryString must_== """foosolr:"3" AND bar:"abcdef" AND baz:"true""""
      }
      "ORs" in {
         """foosolr = 3 OR bar = "abcdef" OR baz = true""".query.toSolrQueryString must_== """foosolr:"3" OR bar:"abcdef" OR baz:"true""""
      }
      "NOT" in {
        """NOT foosolr = 3""".query.toSolrQueryString must_== """NOT foosolr:"3""""
      }
      "nested exprs" in {
        """(foosolr = 3 OR foosolr = 4) AND (bar = true OR (bar = false AND baz = 5))""".query.toSolrQueryString must_== """(foosolr:"3" OR foosolr:"4") AND (bar:"true" OR (bar:"false" AND baz:"5"))"""
      }
      "support unquoted one-word strings" in {
        """foosolr = bar""".query.toSolrQueryString must_== """foosolr:"bar""""
      }
    }

    "type checking" in {
      "keyvals" in {
        val m = AssetMeta.findOrCreateFromName("foosolr", Integer)
        "foosolr = 3".query.typeCheck must_== Right(SolrKeyVal("FOOSOLR_meta_i", SolrIntValue(3)))
        "foosolr = 3.123".query.typeCheck must beAnInstanceOf[Left[String, SolrExpression]]
        "foosolr = true".query.typeCheck must beAnInstanceOf[Left[String, SolrExpression]]
        """foosolr = "3"""".query.typeCheck must_== Right(SolrKeyVal("FOOSOLR_meta_i", SolrIntValue(3)))
      }
      "case insensitive key" in {
        "FoOsOlR = 3".query.typeCheck must_== Right(SolrKeyVal("FOOSOLR_meta_i", SolrIntValue(3)))
      }
      "valid enum" in {
        """type = "SERVER_NODE"""".query.typeCheck must_== Right(SolrKeyVal("TYPE", SolrStringValue("SERVER_NODE", StrictUnquoted)))
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
        "foosolr = [3, 5]".query.typeCheck must_== Right(SolrKeyRange("FOOSOLR_meta_i", Some(SolrIntValue(3)), Some(SolrIntValue(5))))
        "foosolr = [3, *]".query.typeCheck must_== Right(SolrKeyRange("FOOSOLR_meta_i", Some(SolrIntValue(3)), None))
        "foosolr = [*, 5]".query.typeCheck must_== Right(SolrKeyRange("FOOSOLR_meta_i", None, Some(SolrIntValue(5))))
        "foosolr = [*, *]".query.typeCheck must_== Right(SolrKeyRange("FOOSOLR_meta_i", None, None))
        "foosolr = [false, 5]".query.typeCheck must beAnInstanceOf[Left[String, SolrExpression]]
        "foosolr = [3, false]".query.typeCheck must beAnInstanceOf[Left[String, SolrExpression]]
      }
      "not lose NOT" in {
        "NOT foosolr = 3".query.typeCheck must_== Right(SolrNotOp(SolrKeyVal("FOOSOLR_meta_i", SolrIntValue(3))))
      }
      "tag search" in {
        """tag = test""".query.typeCheck must_== Right(SolrKeyVal("TAG", SolrStringValue("test", Quoted)))
      }
      "not allow partial wildcard on numeric values" in {
        """foosolr = 3*""".query.typeCheck must beAnInstanceOf[Left[String, SolrExpression]]
      }
      "TAG can be explicitly wildcarded" in {
        """tag = *foo*""".query.typeCheck must_== Right(SolrKeyVal("TAG", SolrStringValue("foo", LRWildcard)))
      }
    }


  }
  
  "AssetFinder solr conversion" should {
    "basic conversion" in {
      val somedate = new java.util.Date
      val dateString = util.views.Formatter.solrDateFormat(somedate)
      val afinder = AssetFinder(
        Some("foosolrtag"), 
        Status.Allocated, 
        Some(AssetType.ServerNode.get),
        Some(somedate),
        Some(somedate),
        Some(somedate),
        Some(somedate),
        Some(State.Running.get),
        None
      )
      val expected = List(
        SolrKeyVal("tag", SolrStringValue("foosolrtag", Unquoted)),
        SolrKeyVal("status", SolrIntValue(Status.Allocated.get.id)),
        SolrKeyVal("assetType", SolrIntValue(AssetType.ServerNode.get.id)),
        SolrKeyRange("created", Some(SolrStringValue(dateString, StrictUnquoted)),Some(SolrStringValue(dateString, StrictUnquoted))),
        SolrKeyRange("updated", Some(SolrStringValue(dateString, StrictUnquoted)),Some(SolrStringValue(dateString, StrictUnquoted))),
        SolrKeyVal("state", SolrIntValue(State.Running.get.id))
      )
      afinder.toSolrKeyVals.toSet must_== expected.toSet

    }
    "open date ranges" in {
      val somedate = new java.util.Date
      val dateString = util.views.Formatter.solrDateFormat(somedate)
      val afinder = AssetFinder(
        None,
        None,
        None,
        None,
        Some(somedate),
        Some(somedate),
        None,
        None,
        None
      )
      val expected = List(
        SolrKeyRange("updated", Some(SolrStringValue(dateString, StrictUnquoted)),None),
        SolrKeyRange("created",None,Some(SolrStringValue(dateString, StrictUnquoted)))
      )
      afinder.toSolrKeyVals.toSet must_== expected.toSet

    }
    "mix with raw cql query" in {
      val cql = "foo = bar AND (baz = asdf OR abcdef = 3)".query
      val afinder = AssetFinder(
        tag = Some("tagvalue"),
        status = Status.Allocated,
        None,
        None,
        None,
        None,
        None,
        None,
        query = Some(cql)
      )
      val expected = List(
        SolrKeyVal("tag", "tagvalue".unquoted),
        SolrKeyVal("status", SolrIntValue(Status.Allocated.get.id)),
        cql
      )
      afinder.toSolrKeyVals.toSet must_== expected.toSet
    }

  }

  "AssetSearchParameters conversion" should {
    "basic conversion" in {
      //finder
      val somedate = new java.util.Date
      val dateString = util.views.Formatter.solrDateFormat(somedate)
      val afinder = AssetFinder(
        Some("footag"), 
        Some(Status.Allocated.get), 
        AssetType.ServerNode,
        Some(somedate),
        Some(somedate),
        Some(somedate),
        Some(somedate),
        None,
        None
      )
      val ipmiTuples = (IpmiInfo.Enum.IpmiAddress -> "ipmi_address") :: (IpmiInfo.Enum.IpmiUsername -> "ipmi_username") :: Nil
      val metaTuples = (AssetMeta("meta1", 0, "meta1", "meta1") -> "meta1_value") :: (AssetMeta("meta2", 0, "meta2", "meta2") -> "meta2_value") :: Nil
      val ipAddresses = List("1.2.3.4")
      val resultTuple = (ipmiTuples, metaTuples, ipAddresses)

      val expected: SolrExpression = SolrAndOp(List(
        SolrKeyVal("IPMI_ADDRESS", SolrStringValue("ipmi_address", Unquoted)),
        SolrKeyVal("IPMI_USERNAME", SolrStringValue("ipmi_username", Unquoted)),
        SolrKeyVal("meta1", SolrStringValue("meta1_value", Unquoted)),
        SolrKeyVal("meta2", SolrStringValue("meta2_value", Unquoted)),
        SolrKeyVal("ip_address", SolrStringValue("1.2.3.4", Unquoted)),
        SolrKeyRange("created", Some(SolrStringValue(dateString, StrictUnquoted)),Some(SolrStringValue(dateString, StrictUnquoted))),
        SolrKeyRange("updated", Some(SolrStringValue(dateString, StrictUnquoted)),Some(SolrStringValue(dateString, StrictUnquoted))),
        SolrKeyVal("tag", SolrStringValue("footag", Unquoted)),
        SolrKeyVal("status", SolrIntValue(Status.Allocated.get.id)),
        SolrKeyVal("assetType", SolrIntValue(AssetType.ServerNode.get.id))
      ))
      val p = AssetSearchParameters(resultTuple, afinder)
      /*
      println(p.toSolrExpression.toSolrQueryString)
      println("---")
      println(expected.toSolrQueryString)
      */
      p.toSolrExpression must_== expected


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
