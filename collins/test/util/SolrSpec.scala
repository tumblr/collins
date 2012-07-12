package util.plugins

import Solr._
import org.specs2._
import models.{Asset, AssetType, AssetMeta, Status, AssetMetaValue}
import test.ApplicationSpecification

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
      val expected = Map(
        "tag" -> SolrStringValue(assetTag),
        "status" -> SolrStringValue(status.toString),
        "assetType" -> SolrIntValue(assetType.id),
        "created" -> SolrStringValue(asset.created.toString),
        "A_meta_s" -> SolrMultiValue(SolrStringValue("a") :: SolrStringValue("a1") :: Nil),
        "B_meta_s" -> SolrStringValue("b"),
        "INT_meta_i" -> SolrIntValue(1135),
        "DOUBLE_meta_d" -> SolrDoubleValue(3.1415),
        "BOOL_meta_b" -> SolrBooleanValue(false)
      )
      (new FlatSerializer).serialize(asset) must_== expected
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
