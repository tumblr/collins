package models

import util._
import util.parsers.LshwParser

import org.specs2._
import specification._

class LshwHelperSpec extends test.ApplicationSpecification {

  "LSHW Helper Specification".title

  "The LSHW Helper" should {
    "Parse and reconstruct data" in {
      "containing a 10-gig card" in new LshwCommonHelper("lshw-10g.xml") {
        val lshw = parsed()
        val stub = getStub()
        val constructed: Seq[AssetMetaValue] = LshwHelper.construct(stub, lshw)
        val reconstructed = LshwHelper.reconstruct(stub, metaValue2metaWrapper(constructed))._1
        lshw mustEqual reconstructed
      }
      "containing basic info" in new LshwCommonHelper("lshw-basic.xml") {
        val lshw = parsed()
        val stub = getStub()
        val constructed: Seq[AssetMetaValue] = LshwHelper.construct(stub, lshw)
        val reconstructed = LshwHelper.reconstruct(stub, metaValue2metaWrapper(constructed))._1
        lshw mustEqual reconstructed
      }
      "with an intel CPU" in new LshwCommonHelper("lshw-intel.xml") {
        val lshw = parsed()
        val stub = getStub()
        val constructed: Seq[AssetMetaValue] = LshwHelper.construct(stub, lshw)
        val reconstructed = LshwHelper.reconstruct(stub, metaValue2metaWrapper(constructed))._1
        lshw mustEqual reconstructed
      }
      "with modern hardware but old lshw" in new LshwCommonHelper("lshw-new-web-old-lshw.xml") {
        val lshw = parsed()
        val stub = getStub()
        val constructed: Seq[AssetMetaValue] = LshwHelper.construct(stub, lshw)
        val reconstructed = LshwHelper.reconstruct(stub, metaValue2metaWrapper(constructed))._1
        lshw mustEqual reconstructed
      }
      "with old hardware and old lshw" in new LshwCommonHelper("lshw-old-web.xml") {
        val lshw = parsed()
        val stub = getStub()
        val constructed: Seq[AssetMetaValue] = LshwHelper.construct(stub, lshw)
        val reconstructed = LshwHelper.reconstruct(stub, metaValue2metaWrapper(constructed))._1
        lshw mustEqual reconstructed
      }
      "with an older (B.02.12) format" in new LshwCommonHelper("lshw-old.xml") {
        val lshw = parsed()
        val stub = getStub()
        val constructed: Seq[AssetMetaValue] = LshwHelper.construct(stub, lshw)
        val reconstructed = LshwHelper.reconstruct(stub, metaValue2metaWrapper(constructed))._1
        lshw mustEqual reconstructed
      }
      "with a quad NIC" in new LshwCommonHelper("lshw-quad.xml") {
        val lshw = parsed()
        val stub = getStub()
        val constructed: Seq[AssetMetaValue] = LshwHelper.construct(stub, lshw)
        val reconstructed = LshwHelper.reconstruct(stub, metaValue2metaWrapper(constructed))._1
        lshw mustEqual reconstructed
      }
      "containing a virident card" in new LshwCommonHelper("lshw-virident.xml") {
        val lshw = parsed()
        val stub = getStub()
        val constructed: Seq[AssetMetaValue] = LshwHelper.construct(stub, lshw)
        val reconstructed = LshwHelper.reconstruct(stub, metaValue2metaWrapper(constructed))._1
        lshw mustEqual reconstructed
      }
    }

    "update asset meta tags" in {
      "create asset" in new AssetUpdateHelper("lshw-basic.xml") {
        val asset = Asset.create(Asset(assetTag, Status.Enum.Incomplete, AssetType.Enum.ServerNode))
        LshwHelper.updateAsset(asset, parsed())
        asset.getMetaAttribute(AssetMeta.Enum.DiskType) must beSome
      }
      "update asset LSHW with smaller profile" in new AssetUpdateHelper("lshw-small.xml") {
        //lshw-small.xml has no disks
        Asset.findByTag(assetTag).map{asset =>
          LshwHelper.updateAsset(asset, parsed())
          asset.getMetaAttribute(AssetMeta.Enum.DiskType) must beNone
        }.getOrElse(failure("expected to find asset"))
      }
    }
  }

  class LshwCommonHelper(txt: String) extends Scope with test.models.CommonHelperSpec[LshwRepresentation] {
    def getParser(str: String) = new LshwParser(str)
    override def parsed(): LshwRepresentation = getParsed(txt)
  }

  class AssetUpdateHelper(txt: String) extends LshwCommonHelper(txt) {
    val assetTag = "lshw-asset"
  }

}
