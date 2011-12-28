package util
package parsers

import org.specs2.mutable._

class LldpParserSpec extends Specification {

  "The Lldp Parser" should {
    "Parse XML with one network interface" >> {
      val oneNic = getResource("lldpctl-single.xml")
      val parser = new LldpParser(oneNic)
      val parsed = parser.parse()
      parsed must beRight
      val rep = parsed.right.get
      rep.interfaceCount mustEqual(1)
      rep.macAddresses must haveTheSameElementsAs(Seq("78:19:f7:88:60:c0"))
      rep.interfaceNames must haveTheSameElementsAs(Seq("eth0"))
      rep.localPorts must haveTheSameElementsAs(Seq(616))
      rep.chassisNames must haveTheSameElementsAs(Seq("core01.dfw01"))
      rep.vlanNames must haveTheSameElementsAs(Seq("DFW-LOGGING"))
      rep.vlanIds must haveTheSameElementsAs(Seq(106))
    }

    "Parse XML with two network interfaces" >> {
      val twoNic = getResource("lldpctl-two-nic.xml")
      val parser = new LldpParser(twoNic)
      val parsed = parser.parse()
      parsed must beRight
      val rep = parsed.right.get
      rep.interfaceCount mustEqual(2)
      rep.macAddresses must contain("78:19:f7:88:60:c0", "5c:5e:ab:68:a5:80").only
      rep.interfaceNames must contain("eth0", "eth1").only
      rep.localPorts.toSet mustEqual(Set(608))
      rep.chassisNames must contain("core01.dfw01", "core02.dfw01").only
      rep.vlanNames.toSet mustEqual(Set("DFW-LOGGING"))
      rep.vlanIds.toSet mustEqual(Set(106))
    }

    "Fail to parse wrong XML" >> {
      val badXml = getResource("lshw-basic.xml")
      val parser = new LldpParser(badXml)
      val parsed = parser.parse()
      parsed must beLeft
    }

    "Fail to parse text" >> {
      val nonXml = "hello world"
      val parser = new LldpParser(nonXml)
      val parsed = parser.parse()
      parsed must beLeft
    }

    "Fail to parse invalid XML" >> {
      val invalidXml = getResource("lldpctl-bad.xml")
      def getParsed(txt: String) = new LldpParser(txt).parse()

      "Missing chassis name" >> {
        val parsed = getParsed(invalidXml.replace("""<name label="SysName">core01.dfw01</name>""", ""))
        parsed must beLeft
      }
      "Missing chassis description" >> {
        val s = """<descr label="SysDescr">Juniper Networks, Inc. ex4500-40f , version 11.1S1 Build date: 2011-04-21 08:03:12 UTC </descr>"""
        val r = ""
        val parsed = getParsed(invalidXml.replace(s, r))
        parsed must beLeft
      }
      "Missing chassis id type" >> {
        val s = """<id label="ChassisID" type="mac">78:19:f7:88:60:c0</id>"""
        val r = """<id label="ChassisID">78:19:f7:88:60:c0</id>"""
        val parsed = getParsed(invalidXml.replace(s, r))
        parsed must beLeft
      }
      "Missing chassis id value" >> {
        val s = """<id label="ChassisID" type="mac">78:19:f7:88:60:c0</id>"""
        val r = """<id label="ChassisID" type="mac"/>"""
        val parsed = getParsed(invalidXml.replace(s, r))
        parsed must beLeft
      }

      "Missing port id type" >> {
        val s = """<id label="PortID" type="local">616</id>"""
        val r = """<id label="PortID">616</id>"""
        val parsed = getParsed(invalidXml.replace(s, r))
        parsed must beLeft
      }
      "Missing port id value" >> {
        val s = """<id label="PortID" type="local">616</id>"""
        val r = """<id label="PortID" type="local"/>"""
        val parsed = getParsed(invalidXml.replace(s, r))
        parsed must beLeft
      }
      "Missing port description" >> {
        val s = """<descr label="PortDescr">ge-0/0/7.0</descr>"""
        val r = ""
        val parsed = getParsed(invalidXml.replace(s, r))
        parsed must beLeft
      }

      "Missing vlan name" >> {
        val s = """<vlan label="VLAN" vlan-id="106" pvid="yes">DFW-LOGGING</vlan>"""
        val r = """<vlan label="VLAN" vlan-id="106" pvid="yes"/>"""
        val parsed = getParsed(invalidXml.replace(s, r))
        parsed must beLeft
      }
      "Missing vlan id" >> {
        val s = """<vlan label="VLAN" vlan-id="106" pvid="yes">DFW-LOGGING</vlan>"""
        val r = """<vlan label="VLAN" pvid="yes">DFW-LOGGING</vlan>"""
        val parsed = getParsed(invalidXml.replace(s, r))
        parsed must beLeft
      }
    }
  }

  def getResource(filename: String) = {
    val stream = getClass.getClassLoader.getResourceAsStream(filename)
    val tmp = io.Source.fromInputStream(stream)
    val str = tmp.mkString
    tmp.close()
    str
  }

}
