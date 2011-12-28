package models

import util._
import util.parsers.LshwParser
import conversions._
import org.specs2.mutable._
import java.util.Date

class LshwHelperSpec extends Specification {

  args(sequential = true)
  "The LSHW Helper" should {
    "Parse and reconstruct data" in {
      "containing basic info" >> {
        val lshw = getParsed("lshw-basic.xml")
        val stub = getStub()
        val constructed: Seq[AssetMetaValue] = LshwHelper.construct(stub, lshw)
        val reconstructed = LshwHelper.reconstruct(stub, mv2mw(constructed))._1
        lshw mustEqual reconstructed
      }
      "containing a virident card" in {
        val lshw = getParsed("lshw-virident.xml")
        val stub = getStub()
        val constructed: Seq[AssetMetaValue] = LshwHelper.construct(stub, lshw)
        val reconstructed = LshwHelper.reconstruct(stub, mv2mw(constructed))._1
        lshw mustEqual reconstructed
      }
      "containing a 10-gig card" in {
        val lshw = getParsed("lshw-10g.xml")
        val stub = getStub()
        val constructed: Seq[AssetMetaValue] = LshwHelper.construct(stub, lshw)
        val reconstructed = LshwHelper.reconstruct(stub, mv2mw(constructed))._1
        lshw mustEqual reconstructed
      }
      "with an older (B.02.12) format" in {
        val lshw = getParsed("lshw-old.xml")
        val stub = getStub()
        val constructed: Seq[AssetMetaValue] = LshwHelper.construct(stub, lshw)
        val reconstructed = LshwHelper.reconstruct(stub, mv2mw(constructed))._1
        lshw mustEqual reconstructed
      }
      "with an intel CPU" in {
        val lshw = getParsed("lshw-intel.xml")
        val stub = getStub()
        val constructed: Seq[AssetMetaValue] = LshwHelper.construct(stub, lshw)
        val reconstructed = LshwHelper.reconstruct(stub, mv2mw(constructed))._1
        lshw mustEqual reconstructed
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

  def getParsed(filename: String): LshwRepresentation = {
    val data = getResource(filename)
    val parser = new LshwParser(data)
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
