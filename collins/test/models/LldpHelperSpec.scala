package models

import util._
import util.parsers.LldpParser
import conversions._
import org.specs2.mutable._
import java.util.Date

class LldpHelperSpec extends Specification {

  args(sequential = true)
  "The LLDP Helper" should {
    "Parse and reconstruct data" in {
      "with one network interface" >> {
        val lldp = getParsed("lldpctl-single.xml")
        lldp.interfaceCount mustEqual(1)
        val stub = getStub()
        val constructed: Seq[AssetMetaValue] = LldpHelper.construct(stub, lldp)
        val reconstructed = LldpHelper.reconstruct(stub, mv2mw(constructed))._1
        lldp mustEqual reconstructed
      }
      "with two network interfaces" >> {
        val lldp = getParsed("lldpctl-two-nic.xml")
        lldp.interfaceCount mustEqual(2)
        val stub = getStub()
        val constructed: Seq[AssetMetaValue] = LldpHelper.construct(stub, lldp)
        val reconstructed = LldpHelper.reconstruct(stub, mv2mw(constructed))._1
        lldp mustEqual reconstructed
      }
    }
  }

  def mv2mw(mvs: Seq[AssetMetaValue]): Seq[MetaWrapper] = {
    val enums = AssetMeta.Enum.values.toSeq
    mvs.map { mv =>
      val mid = mv.asset_meta_id.get
      val meta = enums.find { e => e.id == mid }.map { e =>
        AssetMeta(mv.asset_meta_id, e.toString, -1, e.toString, e.toString)
      }.getOrElse(throw new Exception("Found unhandled AssetMeta"))
      MetaWrapper(meta, mv)
    }
  }

  def getStub(): Asset = {
    Asset(anorm.Id(1), "test", Status.Enum.New.id, AssetType.Enum.ServerNode.id, new Date().asTimestamp, None, None)
  }

  def getParsed(filename: String): LldpRepresentation = {
    val data = getResource(filename)
    val parser = new LldpParser(data)
    val parsed = parser.parse()
    parsed.right.get
  }

  def getResource(filename: String) = {
    val stream = getClass.getClassLoader.getResourceAsStream(filename)
    val tmp = io.Source.fromInputStream(stream)
    val str = tmp.mkString
    tmp.close()
    str
  }


}
