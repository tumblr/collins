package collins.solr

import java.util.Date
import Solr._
import org.specs2._
import models.{Asset, AssetFinder, AssetType, AssetMeta, AssetSearchParameters, IpAddresses, IpmiInfo, State, Status, AssetMetaValue}
import test.ApplicationSpecification
import util.views.Formatter


class MultiSetSpec extends mutable.Specification {

  "MultiSet" should {
    "add a new element" in {
      (MultiSet[Int]() + 3).items must_== Map(3 -> 1)
    }
    "vararg constructor" in {
      MultiSet(1,2,2,3).items must_== Map(1 -> 1, 2 -> 2, 3 -> 1)
    }
    "add an existing element" in {
      (MultiSet(1,1,1) + 1).items must_== Map(1 -> 4)
    }
    "size" in {
      MultiSet(1, 2, 2, 3, 4, 5).size must_== 6
    }
    "headOption" in {
      MultiSet().headOption must_== None
      MultiSet(1,2,3,4).headOption must_== Some(1)
    }
    "toSeq" in { 
      MultiSet(1, 2, 2, 3).toSeq must_== Seq(1,2,2,3)
    }
    "equals" in {
      MultiSet(1,2, 3, 2) must_== MultiSet(1, 2, 2, 3)
    }
  }
}

class SolrSpec extends ApplicationSpecification {

  import AssetMeta.ValueType._
  import AssetMeta.ValueType

  args(sequential = true)

  "AssetSerializer" should {
    def eqHelper[T](actualSet: Set[T], expectedSet: Set[T]) {
      if (expectedSet != actualSet) {
        println("== EXPECTED ==")
        expectedSet.foreach{e => println(e.toString)}
        println("== ACTUAL ==")
        actualSet.foreach{a => println(a.toString)}
        println("== expected - actual ==")
        (expectedSet diff actualSet).foreach{e => println(e.toString)}
        println("== actual - expected ==")
        (actualSet diff expectedSet).foreach{e => println(e.toString)}
      }
    }

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
      val indexTime = new Date
      val addresses = IpAddresses.createForAsset(asset, 2, Some("DEV"))
      val ipmi = IpmiInfo.createForAsset(asset)
      val almostExpected = Map(
        SolrKey("DOC_TYPE", String, false, false, true) -> SolrStringValue("ASSET", StrictUnquoted),
        SolrKey("LAST_INDEXED", String, false, false, true) -> SolrStringValue(Formatter.solrDateFormat(indexTime), StrictUnquoted),
        SolrKey("UUID", String, false, false, true) -> SolrStringValue("ASSET_" + asset.id, StrictUnquoted),
        SolrKey("ID", Integer, false, false,true) -> SolrIntValue(asset.id.toInt),
        SolrKey("TAG", String, false, false, true) -> SolrStringValue(assetTag, StrictUnquoted),
        SolrKey("STATUS", String, false, false, true) -> SolrStringValue(status.name, StrictUnquoted),
        SolrKey("STATE", String, false, false, true) -> SolrStringValue(state.name, StrictUnquoted),
        SolrKey("TYPE", String, false, false, true, Set("ASSETTYPE")) -> SolrStringValue(assetType.name, StrictUnquoted),
        SolrKey("CREATED", String, false, false, true) -> SolrStringValue(Formatter.solrDateFormat(asset.created), StrictUnquoted),
        SolrKey("A", String, true, true, false) -> SolrMultiValue(MultiSet(SolrStringValue("a", StrictUnquoted), SolrStringValue("a1", StrictUnquoted))),
        SolrKey("B", String, true, true, false) -> SolrStringValue("b", StrictUnquoted),
        SolrKey("INT", Integer, true, true, false) -> SolrIntValue(1135),
        SolrKey("DOUBLE", Double, true, true, false) -> SolrDoubleValue(3.1415),
        SolrKey("BOOL", Boolean, true, true, false) -> SolrBooleanValue(false),
        SolrKey("IP_ADDRESS", String, false, true, false) -> SolrMultiValue(MultiSet.fromSeq(addresses.map{a => SolrStringValue(a.dottedAddress, StrictUnquoted)})),
        SolrKey("HOSTNAME", String, false, false, true) -> SolrStringValue("my_hostname", StrictUnquoted),
        SolrKey("IPMI_ADDRESS", String, true, false, true) -> SolrStringValue(ipmi.dottedAddress, StrictUnquoted)
      )

      val sortKeys = Map(
        SolrKey("DOC_TYPE_SORT", String, false, false, true) -> SolrStringValue("ASSET", StrictUnquoted),
        SolrKey("LAST_INDEXED_SORT", String, false, false, true) -> SolrStringValue(Formatter.solrDateFormat(indexTime), StrictUnquoted),
        SolrKey("UUID_SORT", String, false, false, true) -> SolrStringValue("ASSET_" + asset.id, StrictUnquoted),
        SolrKey("ID_SORT", String, false,false, true) -> SolrStringValue(asset.id.toString, StrictUnquoted),
        SolrKey("TAG_SORT", String, false,false, true) -> SolrStringValue(assetTag, StrictUnquoted),
        SolrKey("STATUS_SORT", String, false,false, true) -> SolrStringValue(status.name, StrictUnquoted),
        SolrKey("STATE_SORT", String, false,false, true) -> SolrStringValue(state.name, StrictUnquoted),
        SolrKey("TYPE_SORT", String, false,false, true) -> SolrStringValue(assetType.name, StrictUnquoted),
        SolrKey("CREATED_SORT", String, false,false, true) -> SolrStringValue(Formatter.solrDateFormat(asset.created), StrictUnquoted),
        SolrKey("HOSTNAME_SORT", String, false,false, true) -> SolrStringValue("my_hostname", StrictUnquoted),
        SolrKey("IPMI_ADDRESS_SORT", String, false,false, true) -> SolrStringValue(ipmi.dottedAddress, StrictUnquoted)
      )

      val expected = almostExpected 
        .++(sortKeys)
        .+((SolrKey("KEYS", String, true, true, false) -> SolrMultiValue(MultiSet.fromSeq(almostExpected.map{case(k,v) => SolrStringValue(k.name, StrictUnquoted)}.toSeq), String)))
      val actual = (new AssetSerializer).serialize(asset, indexTime) 
      val actualSet: Set[(SolrKey, SolrValue)] = actual.toSet
      val expectedSet: Set[(SolrKey, SolrValue)] = expected.toSet
      eqHelper(actualSet, expectedSet)
      actualSet must_== expectedSet
    }
    "post-process number of disks" in {
      val m = Map[SolrKey, SolrValue](SolrKey("DISK_SIZE_BYTES", String, true, true,false) -> SolrMultiValue(MultiSet(SolrStringValue("123", StrictUnquoted), SolrStringValue("123", StrictUnquoted))))
      val expected = m + 
        (SolrKey("NUM_DISKS", Integer, true, false, true) -> SolrIntValue(2)) + 
        (SolrKey("NUM_DISKS_SORT", String, false, false, true) -> SolrStringValue("2", StrictUnquoted)) + 
        (SolrKey("KEYS", String, true, true, false) -> SolrMultiValue(MultiSet(SolrStringValue("DISK_SIZE_BYTES", StrictUnquoted), SolrStringValue("NUM_DISKS", StrictUnquoted))))
      val actual = (new AssetSerializer).postProcess(m)
      val actualSet = actual.toSet
      val expectedSet = expected.toSet
      eqHelper(actualSet, expectedSet)
      actualSet must_== expectedSet
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

  def P = CollinsQueryParser()

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
    "empty.query.where" in {
      "*".query.where must_== EmptySolrQuery
    }

    "selects" in {
      "defaults to asset" in {
        "foo = bar".query.select must_== AssetDocType
      }
      "select assets" in {
        "SELECT asset WHERE foo = bar".query.select must_== AssetDocType
      }
      "select logs" in {
        val p = CollinsQueryParser(List(AssetDocType, AssetLogDocType))
        p.parseQuery("SELECT asset_log WHERE foo = bar").right.get.select must_== AssetLogDocType
      }
      "Reject unknown select type" in {
        CollinsQueryParser().parseQuery("SELECT omgwtfbbq WHERE foo = bar") must beAnInstanceOf[Left[String, CQLQuery]]
      }

      "clean string" in {
        "trim whitespace" in {
          """ 
              foo = bar
            """.query.where must_== SolrKeyVal("foo", SolrStringValue("bar"))
         }
        "remove enclosing quotes" in {
          """   "foo = bar" """.query.where must_== SolrKeyVal("foo", SolrStringValue("bar"))
        }
      }

    }

    "SolrKey" should {
      "properly convert names to U case" in {
        val n: String = SolrKey("foo", String, false, false, false).name 
        n must_== "FOO"
      }
      "convert aliases to UCASE" in {
        SolrKey("foo", String, false, false, false, Set("bar", "BAZ")).isAliasOf("bar") must beTrue
        SolrKey("foo", String, false, false, false, Set("bar", "BAZ")).isAliasOf("baz") must beTrue
      }
      "force multivalued keys to be non-sortable" in {
        SolrKey("foo", String, false, true, true).sortKey must_== None
      }
    }

    "key-value" in {
      "string value" in {
        """foosolr = "bar"""".query.where must_== (("foosolr" -> "bar".quoted): SolrKeyVal)
      }
      "int value" in {
        """foosolr = 3""".query.where must_== (("foosolr" -> "3"): SolrKeyVal)
      }
      "double value" in {
        """foosolr = 3.1415""".query.where must_== (("foosolr" -> "3.1415"): SolrKeyVal)
      }
      "boolean value" in {
        """foosolr = false""".query.where must_== (("foosolr" -> "false"): SolrKeyVal)
      }
      "leading regex wildcard" in {
        """foosolr = .*bar""".query.where must_== SolrKeyVal("foosolr", SolrStringValue("bar", LWildcard))
      }
      "number-start string value" in {
        """foosolr = 03abc.xyz09-wer:10""".query.where must_== SolrKeyVal("foosolr", SolrStringValue("03abc.xyz09-wer:10", Unquoted))
      }
      "unquoted mac address" in {
        """foosolr = 04:7d:7b:06:8f:f9""".query.where must_== SolrKeyVal("foosolr", SolrStringValue("04:7d:7b:06:8f:f9", Unquoted))
      }
      "ip address" in {
        """ip_address = "192.168.1.1"""".query.where must_== SolrKeyVal("ip_address", SolrStringValue("192.168.1.1", Quoted))
      }
      "unquoted ip address" in {
        """ip_address = 192.168.1.1""".query.where must_== SolrKeyVal("ip_address", SolrStringValue("192.168.1.1", Unquoted))
        """ip_address = 192.168.1.*""".query.where must_== SolrKeyVal("ip_address", SolrStringValue("192.168.1", RWildcard))
        """ip_address = 192.168.*""".query.where must_== SolrKeyVal("ip_address", SolrStringValue("192.168", RWildcard))
        """ip_address = 192.*""".query.where must_== SolrKeyVal("ip_address", SolrStringValue("192", RWildcard))
        """ip_address = *""".query.where must_== SolrKeyVal("ip_address", SolrStringValue("*", FullWildcard))
      }
    }

    "ranges" in {
      "both inclusive" in {
        """foosolr = [3, 5]""".query.where must_== SolrKeyRange("foosolr", Some(SolrStringValue("3", StrictUnquoted)), Some(SolrStringValue("5", StrictUnquoted)), true)
      }
      "range opt low" in {
        """foosolr = [*, 5]""".query.where must_== SolrKeyRange("foosolr", None, Some(SolrStringValue("5", StrictUnquoted)), true)
      }
      "range opt high" in {
        """foosolr = [3, *]""".query.where must_== SolrKeyRange("foosolr", Some(SolrStringValue("3", StrictUnquoted)), None, true)
      }
      "range opt both" in {
        """foosolr = [*, *]""".query.where must_== SolrKeyRange("foosolr", None, None, true)
      }
      "open range" in {
        """foosolr = (3, 5)""".query.where must_== SolrKeyRange("foosolr", Some(SolrStringValue("3", StrictUnquoted)), Some(SolrStringValue("5", StrictUnquoted)), false)
      }
      "clopen range" in {
        val p1 = SolrKeyRange("foosolr", Some(SolrStringValue("3", StrictUnquoted)), None, true)
        val p2 = SolrKeyRange("foosolr", None, Some(SolrStringValue("5", StrictUnquoted)), false)
        """foosolr = [3, 5)""".query.where must_== (p1 AND p2)
      }
      ">" in {
        """foosolr > 3""".query.where must_== SolrKeyRange("foosolr", Some(SolrStringValue("3", StrictUnquoted)), None, false)
      }
      ">=" in {
        """foosolr >= 3""".query.where must_== SolrKeyRange("foosolr", Some(SolrStringValue("3", StrictUnquoted)), None, true)
      }
      "<" in {
        """foosolr < 5""".query.where must_== SolrKeyRange("foosolr", None, Some(SolrStringValue("5", StrictUnquoted)), false)
      }
      "<=" in {
        """foosolr <= 5""".query.where must_== SolrKeyRange("foosolr", None, Some(SolrStringValue("5", StrictUnquoted)), true)
      }
      "< date" in {
        val t = new Date
        val s = Formatter.solrDateFormat(t)
        "foosolr < %s".format(s).query.where must_== SolrKeyRange("foosolr", None, Some(SolrStringValue(s, StrictUnquoted)), false)
      }

    }

    "complex expressions" in {
      "simple AND" in {
        """foosolr = 3 AND bar = 4""".query.where must_== (("foosolr" -> "3") AND ("bar" -> "4"))
      }
      "simple OR" in {
        """foosolr = 3 OR bar = 4""".query.where must_== (("foosolr" -> "3") OR ("bar" -> "4"))
      }
      "case insensitive AND" in {
        """foosolr = 3 and bar = 4""".query must_== """foosolr = 3 AND bar = 4""".query
      }
      "case insensitive OR" in {
        """foosolr = 3 or bar = 4""".query must_== """foosolr = 3 OR bar = 4""".query
      }
      "order of operations" in {
        """foosolr = 4 OR bar = 4 AND baz = false""".query.where must_== (("foosolr" -> "4") OR ("bar" -> "4" AND "baz" -> "false"))
      }
      "arbitrary parentheses" in {
        """(((((((foosolr = true)))))))""".query.where must_== SolrKeyVal("foosolr", SolrStringValue("true", Unquoted))
      }
      "simple NOT" in {
        """NOT foosolr = 5""".query.where must_== CollinsQueryDSL.not("foosolr" -> "5")
      }
      "case insensitive NOT" in {
        """not foosolr = 5""".query must_== """NOT foosolr = 5""".query
      }
      "not OOO" in {
        """NOT foosolr = 5 OR bar = false""".query.where must_== (SolrNotOp(("foosolr" -> "5")) OR ("bar" -> "false"))
      }
      "negate complex expression" in {
        """NOT (foosolr = 5 AND bar = "baz")""".query.where must_== SolrNotOp(("foosolr" -> "5") AND ("bar" -> "baz".quoted))
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
        S("04:7d:7b:06:8f:f9", Q).traverseQueryString(false) must_== """"04:7d:7b:06:8f:f9""""
      }
      "wildcard" in {
        S("04:7d:7b:06:8",R).traverseQueryString(false) must_== """04\:7d\:7b\:06\:8*"""
      }
      "strict unquoted" in {
        S("04:7d:7b:06:8f:f9", StrictUnquoted).traverseQueryString(false) must_== """04\:7d\:7b\:06\:8f\:f9"""
      }
    }
  } 


  "CQL abstract syntax-tree" should {

    "solr query generation" in {
      "empty query" in {
        "*".query.where.traverseQueryString must_== "*:*"
      }
      "field wildcard" in {
        "tag = *".query.where.traverseQueryString must_== "tag:*"
      }
      "simple keyval" in {
        //the quotes are expected since it hasn't type inferred to an int yet
        "foosolr = 3".query.where.traverseQueryString must_== """foosolr:"3""""
      }
      "not pad unquoted unchecked strings with wildcards" in {
        "foo = bar".query.where.traverseQueryString must_== """foo:"bar""""
      }
      "handle ^" in {
        "foo = ^bar".query.where.traverseQueryString must_== """foo:bar*"""
      }
      "handle $" in {
        "foo = bar$".query.where.traverseQueryString must_== """foo:*bar"""
      }
      "handle both ^ and $" in {
        "foo = ^bar$".query.where.traverseQueryString must_== """foo:"bar""""
      }
      "not handle ^ or $ in quoted string" in {
        """foo = "^bar$"""".query.where.traverseQueryString must_== """foo:"^bar$""""
      }
      "quoted dash" in {
        """tag=-""".query.where.traverseQueryString must_== """tag:"-""""
      }
      "leading wildcard" in {
        """hostname=*foo""".query.where.traverseQueryString must_== """hostname:*foo"""
      }
      "trailing wildcard" in {
        """hostname=foo*""".query.where.traverseQueryString must_== """hostname:foo*"""
      }
      "not quote ranges" in {
        """foo = [abc, abd]""".query.where.traverseQueryString must_== """foo:[abc TO abd]"""
      }
      "ANDs" in {
         """foosolr = 3 AND bar = "abcdef" AND baz = true""".query.where.traverseQueryString must_== """foosolr:"3" AND bar:"abcdef" AND baz:"true""""
      }
      "ORs" in {
         """foosolr = 3 OR bar = "abcdef" OR baz = true""".query.where.traverseQueryString must_== """foosolr:"3" OR bar:"abcdef" OR baz:"true""""
      }
      "NOT" in {
        """NOT foosolr = 3""".query.where.traverseQueryString must_== """NOT foosolr:"3""""
      }
      "nested exprs" in {
        """(foosolr = 3 OR foosolr = 4) AND (bar = true OR (bar = false AND baz = 5))""".query.where.traverseQueryString must_== """(foosolr:"3" OR foosolr:"4") AND (bar:"true" OR (bar:"false" AND baz:"5"))"""
      }
      "support unquoted one-word strings" in {
        """foosolr = bar""".query.where.traverseQueryString must_== """foosolr:"bar""""
      }
    }

    "type checking" in {

      def A(s: SolrExpression) = ("DOC_TYPE" -> "ASSET".strictUnquoted) AND s
      "keyvals" in {
        val m = AssetMeta.findOrCreateFromName("foosolr", Integer)
        "foosolr = 3".query.typeCheck must_== Right(A(SolrKeyVal("FOOSOLR_meta_i", SolrIntValue(3))))
        "foosolr = 3.123".query.typeCheck must beAnInstanceOf[Left[String, SolrExpression]]
        "foosolr = true".query.typeCheck must beAnInstanceOf[Left[String, SolrExpression]]
        """foosolr = "3"""".query.typeCheck must_== Right(A(SolrKeyVal("FOOSOLR_meta_i", SolrIntValue(3))))
      }
      "case insensitive key" in {
        "FoOsOlR = 3".query.typeCheck must_== Right(A(SolrKeyVal("FOOSOLR_meta_i", SolrIntValue(3))))
      }
      "valid enum" in {
        """type = "SERVER_NODE"""".query.typeCheck must_== Right(A(SolrKeyVal("TYPE", SolrStringValue("SERVER_NODE", StrictUnquoted))))
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
        """num_disks = 3""".query.typeCheck must_== Right(A(SolrKeyVal("NUM_DISKS_meta_i", SolrIntValue(3))))
      }
      "AND" in {
        "foosolr = 3 AND foosolr = false".query.typeCheck must beAnInstanceOf[Left[String, SolrExpression]]
      }
      "OR" in {
        "foosolr = 3 OR foosolr = false".query.typeCheck must beAnInstanceOf[Left[String, SolrExpression]]
      }
      "range" in {
        "foosolr = [3, 5]".query.typeCheck must_== Right(A(SolrKeyRange("FOOSOLR_meta_i", Some(SolrIntValue(3)), Some(SolrIntValue(5)), true)))
        "foosolr = [3, *]".query.typeCheck must_== Right(A(SolrKeyRange("FOOSOLR_meta_i", Some(SolrIntValue(3)), None, true)))
        "foosolr = [*, 5]".query.typeCheck must_== Right(A(SolrKeyRange("FOOSOLR_meta_i", None, Some(SolrIntValue(5)), true)))
        "foosolr = [*, *]".query.typeCheck must_== Right(A(SolrKeyRange("FOOSOLR_meta_i", None, None, true)))
        "foosolr = [false, 5]".query.typeCheck must beAnInstanceOf[Left[String, SolrExpression]]
        "foosolr = [3, false]".query.typeCheck must beAnInstanceOf[Left[String, SolrExpression]]
      }
      "not lose NOT" in {
        "NOT foosolr = 3".query.typeCheck must_== Right(A(SolrNotOp(SolrKeyVal("FOOSOLR_meta_i", SolrIntValue(3)))))
      }
      "tag search" in {
        """tag = test""".query.typeCheck must_== Right(A(SolrKeyVal("TAG", SolrStringValue("test", Quoted))))
      }
      "not allow partial wildcard on numeric values" in {
        """foosolr = 3*""".query.typeCheck must beAnInstanceOf[Left[String, SolrExpression]]
      }
      "TAG can be explicitly wildcarded" in {
        """tag = *foo*""".query.typeCheck must_== Right(A(SolrKeyVal("TAG", SolrStringValue("foo", LRWildcard))))
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
        SolrKeyRange("created", Some(SolrStringValue(dateString, StrictUnquoted)),Some(SolrStringValue(dateString, StrictUnquoted)), true),
        SolrKeyRange("updated", Some(SolrStringValue(dateString, StrictUnquoted)),Some(SolrStringValue(dateString, StrictUnquoted)), true),
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
        SolrKeyRange("updated", Some(SolrStringValue(dateString, StrictUnquoted)),None, true),
        SolrKeyRange("created",None,Some(SolrStringValue(dateString, StrictUnquoted)), true)
      )
      afinder.toSolrKeyVals.toSet must_== expected.toSet

    }
    "mix with raw cql query" in {
      val cql = "foo = bar AND (baz = asdf OR abcdef = 3)".query.where
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

      val expected: SolrExpression = SolrAndOp(Set(
        SolrKeyVal("IPMI_ADDRESS", SolrStringValue("ipmi_address", Unquoted)),
        SolrKeyVal("IPMI_USERNAME", SolrStringValue("ipmi_username", Unquoted)),
        SolrKeyVal("meta1", SolrStringValue("meta1_value", Unquoted)),
        SolrKeyVal("meta2", SolrStringValue("meta2_value", Unquoted)),
        SolrKeyVal("ip_address", SolrStringValue("1.2.3.4", Unquoted)),
        SolrKeyRange("created", Some(SolrStringValue(dateString, StrictUnquoted)),Some(SolrStringValue(dateString, StrictUnquoted)), true),
        SolrKeyRange("updated", Some(SolrStringValue(dateString, StrictUnquoted)),Some(SolrStringValue(dateString, StrictUnquoted)), true),
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
