package util
package parsers

import test.util.parsers.CommonParserSpec

import org.specs2._
import specification._

class LldpParserSpec extends mutable.Specification {

  class LldpParserHelper(val filename: String) extends Scope with CommonParserSpec[LldpRepresentation] {
    override def getParser(txt: String, options: Map[String,String] = Map.empty) = new LldpParser(txt, options)
    def parsed() = getParseResults(filename)
  }

  "The Lldp Parser" should {
    "Parse XML with one network interface" in new LldpParserHelper("lldpctl-single.xml") {
      val parseResult = parsed()
      parseResult must beRight
      parseResult.right.toOption must beSome.which { rep =>
        rep.interfaceCount mustEqual(1)
        rep.macAddresses must haveTheSameElementsAs(Seq("78:19:f7:88:60:c0"))
        rep.interfaceNames must haveTheSameElementsAs(Seq("eth0"))
        rep.localPorts must haveTheSameElementsAs(Seq(616))
        rep.chassisNames must haveTheSameElementsAs(Seq("core01.dfw01"))
        rep.vlanNames must haveTheSameElementsAs(Seq("DFW-LOGGING"))
        rep.vlanIds must haveTheSameElementsAs(Seq(106))
      }
    }

    "Parse XML with two network interfaces" in new LldpParserHelper("lldpctl-two-nic.xml") {
      val parseResult = parsed()
      parseResult must beRight
      parseResult.right.toOption must beSome.which { rep =>
        rep.interfaceCount mustEqual(2)
        rep.macAddresses must contain("78:19:f7:88:60:c0", "5c:5e:ab:68:a5:80").only
        rep.interfaceNames must contain("eth0", "eth1").only
        rep.localPorts.toSet mustEqual(Set(608))
        rep.chassisNames must contain("core01.dfw01", "core02.dfw01").only
        rep.vlanNames.toSet mustEqual(Set("DFW-LOGGING"))
        rep.vlanIds.toSet mustEqual(Set(106))
      }
    }

    "Parse XML with four network interfaces" in new LldpParserHelper("lldpctl-four-nic.xml") {
      val parseResult = parsed()
      parseResult must beRight
      parseResult.right.toOption must beSome.which { rep =>
        rep.interfaceCount mustEqual(3)
        rep.macAddresses must contain(
          "2c:21:72:96:93:00", "28:c0:da:b9:5b:f0", "84:18:88:9c:57:f0").only
        rep.interfaceNames must contain("eth0", "eth4", "eth5").only
        rep.localPorts.toSet mustEqual(Set(588,2113,1488))
        rep.chassisNames must contain(
          "oob-switch013.ewr01", "re0.access-switch01.ewr01", "re0.access-switch02.ewr01").only
        rep.vlanNames.toSet mustEqual(Set("EWR-PROVISIONING","OOB-NETWORK","OOB-POWER","OOB-SERVERS"))
        rep.vlanIds.toSet mustEqual(Set(104,115,114,108))
      }
    }

    "Parse a generated XML file" in new LldpParserHelper("lldpctl-empty.xml") {
      parsed must beRight
    }

    "Fail to parse wrong XML" in new LldpParserHelper("lshw-basic.xml") {
      parsed must beLeft
    }

    "Fail to parse text" in new LldpParserHelper("hello world") {
      getParser(filename).parse() must beLeft
    }

    "Fail to parse invalid XML" in new LldpParserHelper("lldpctl-bad.xml") {
      val invalidXml = getResource(filename)
      override def getParseResults(data: String, opts: Map[String,String] = Map.empty): Either[Throwable,LldpRepresentation] = {
        getParser(data, opts).parse()
      }

      "Missing chassis name" >> {
        getParseResults(invalidXml.replace("""<name label="SysName">core01.dfw01</name>""", "")) must beLeft
      }
      "Missing chassis description" >> {
        val s = """<descr label="SysDescr">Juniper Networks, Inc. ex4500-40f , version 11.1S1 Build date: 2011-04-21 08:03:12 UTC </descr>"""
        val r = ""
        getParseResults(invalidXml.replace(s, r)) must beLeft
      }
      "Missing chassis id type" >> {
        val s = """<id label="ChassisID" type="mac">78:19:f7:88:60:c0</id>"""
        val r = """<id label="ChassisID">78:19:f7:88:60:c0</id>"""
        getParseResults(invalidXml.replace(s, r)) must beLeft
      }
      "Missing chassis id value" >> {
        val s = """<id label="ChassisID" type="mac">78:19:f7:88:60:c0</id>"""
        val r = """<id label="ChassisID" type="mac"/>"""
        getParseResults(invalidXml.replace(s, r)) must beLeft
      }

      "Missing port id type" >> {
        val s = """<id label="PortID" type="local">616</id>"""
        val r = """<id label="PortID">616</id>"""
        getParseResults(invalidXml.replace(s, r)) must beLeft
      }
      "Missing port id value" >> {
        val s = """<id label="PortID" type="local">616</id>"""
        val r = """<id label="PortID" type="local"/>"""
        getParseResults(invalidXml.replace(s, r)) must beLeft
      }
      "Missing port description" >> {
        val s = """<descr label="PortDescr">ge-0/0/7.0</descr>"""
        val r = ""
        getParseResults(invalidXml.replace(s, r)) must beLeft
      }

      "Missing vlan name" >> {
        val s = """<vlan label="VLAN" vlan-id="106" pvid="yes">DFW-LOGGING</vlan>"""
        val r = """<vlan label="VLAN" vlan-id="106" pvid="yes"/>"""
        getParseResults(invalidXml.replace(s, r)) must beLeft
      }
      "Missing vlan id" >> {
        val s = """<vlan label="VLAN" vlan-id="106" pvid="yes">DFW-LOGGING</vlan>"""
        val r = """<vlan label="VLAN" pvid="yes">DFW-LOGGING</vlan>"""
        getParseResults(invalidXml.replace(s, r)) must beLeft
      }
    }
  }

}
