package models

import util._
import util.parsers.LldpParser
import org.specs2._
import specification._

class LldpHelperSpec extends mutable.Specification {

  "LLDP Helper Specification".title

  "The LLDP Helper" should {
    "Parse and reconstruct data" in {
      "with one network interface" in new LldpCommonHelper("lldpctl-single.xml") {
        val lldp = parsed()
        lldp.interfaceCount mustEqual(1)
        val stub = getStub()
        val constructed: Seq[AssetMetaValue] = LldpHelper.construct(stub, lldp)
        val reconstructed = LldpHelper.reconstruct(stub, metaValue2metaWrapper(constructed))._1
        lldp mustEqual reconstructed
      }
      "with two network interfaces" in new LldpCommonHelper("lldpctl-two-nic.xml") {
        val lldp = parsed()
        lldp.interfaceCount mustEqual(2)
        val stub = getStub()
        val constructed: Seq[AssetMetaValue] = LldpHelper.construct(stub, lldp)
        val reconstructed = LldpHelper.reconstruct(stub, metaValue2metaWrapper(constructed))._1
        lldp mustEqual reconstructed
      }
      "with four network interfaces" in new LldpCommonHelper("lldpctl-quad.xml") {
        val lldp = parsed()
        lldp.interfaceCount mustEqual(2)
        val stub = getStub()
        val constructed: Seq[AssetMetaValue] = LldpHelper.construct(stub, lldp)
        val reconstructed = LldpHelper.reconstruct(stub, metaValue2metaWrapper(constructed))._1
        lldp mustEqual reconstructed
      }
    }
  }

  class LldpCommonHelper(txt: String) extends Scope with test.models.CommonHelperSpec[LldpRepresentation] {
    def getParser(str: String) = new LldpParser(str)
    override def parsed(): LldpRepresentation = getParsed(txt)
  }

}
