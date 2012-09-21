package models

import test.ApplicationSpecification

import org.specs2._
import specification._

class AssetSpec extends ApplicationSpecification {

  "Asset Model Specification".title

  args(sequential = true)

  "The Asset Model" should {

    "Support CRUD Operations" in {

      "CREATE" in new mockasset {
        val result = Asset.create(newAsset)
        result.getId must beGreaterThan(1L)
      }

      "UPDATE" in new mockasset {
        val maybeAsset = Asset.findByTag(assetTag)
        maybeAsset must beSome[Asset]
        val realAsset = maybeAsset.get
        Asset.update(realAsset.copy(status = Status.New.get.id))
        Asset.findByTag(assetTag).map { a =>
          a.getStatus().getId mustEqual(Status.New.get.id)
        }.getOrElse(failure("Couldn't find asset but expected to"))
      }

      "DELETE" in new mockasset {
        Asset.findByTag(assetTag).map { a =>
          Asset.delete(a) mustEqual 1
          Asset.findById(a.getId) must beNone
        }.getOrElse(failure("Couldn't find asset but expected to"))
      }
    }

    "Support nodeclass" in {
      
      "nodeClass" in new mocknodeclass {
        val nodeclass = Asset.create(Asset(nodeclassTag, nodeclassStatus,nodeclassType))
        val testAsset = Asset.create(Asset(assetTag, assetStatus, assetType))
        val nodeclassMetas = createAssetMetas(nodeclass, (nodeclassMetaTags + nodeclassIdentifierTag))
        val assetMetas = createAssetMetas(testAsset, nodeclassMetaTags)
        testAsset.nodeClass must_== Some(nodeclass)
      }

      "findSimilar" in new mocknodeclass {
        val assets = similarAssetData.map{case (tag, status, metatags) => {
          val asset = Asset.create(Asset(tag, status, AssetType.ServerNode.get))
          createAssetMetas(asset, metatags)
          asset
        }}
        val finder = AssetFinder.empty.copy(
          status = Status.Unallocated,
          assetType = Some(AssetType.ServerNode.get)
        )
        val expected = assets.filter{_.tag == similarAssetTag}
        val page = PageParams(0,10, "")
        Asset.findByTag(assetTag).map{asset =>
          Asset.findSimilar(asset, page, finder, AssetSortType.Distribution).items must_== expected
        }.getOrElse(failure("Couldn't find asset but expected to"))

      }

    } //support nodeclass
        

    "Support getters/finders" in {

      "findByTag" in new concreteasset {
        Asset.findByTag(assetTag) must beSome[Asset]
        Asset.findByTag(assetTag).get.tag mustEqual assetTag
      }

      "findLikeTag" in new concreteasset {
        val page = PageParams(0, 10, "")
        val assets = Asset.findLikeTag(assetTag.take(assetTag.size - 1), page)
        assets.total must beGreaterThan(0L)
        assets.items must have {_.tag == assetTag}
      }

      

      "getAllAttributes" in new concreteasset {
        val maybeAsset = Asset.findByTag(assetTag)
        maybeAsset must beSome[Asset]
        val asset = maybeAsset.get
        val attributes = asset.getAllAttributes
        attributes.ipmi must beSome.which { ipmi =>
          ipmi.dottedAddress mustEqual "10.0.0.2"
          ipmi.dottedGateway mustEqual "10.0.0.1"
        }
      }

    } // support getters/finders
  } // Asset should

  trait mockasset extends Scope {
    val assetTag = "tumblrtag2"
    val assetStatus = Status.Incomplete.get
    val assetType = AssetType.ServerNode.get
    val newAsset = Asset(assetTag, assetStatus, assetType)
  }

  trait concreteasset extends Scope {
    val assetTag = "tumblrtag1"
    val assetStatus = Status.Incomplete.get
    val assetType = AssetType.ServerNode.get
    val assetId = 1
  }

  trait mocknodeclass extends Scope {
    def createAssetMetas(asset: Asset, metamap: Map[String, String]) = metamap
      .map{ case (k,v) => 
        AssetMetaValue.create(AssetMetaValue(asset.id, AssetMeta.findOrCreateFromName(k).id, 0, v))
      }
    val nodeclassTag = "test_nodeclass"
    val nodeclassStatus = Status.Allocated.get
    val nodeclassType = AssetType.Configuration.get
    val nodeclassIdentifierTag = ("IS_NODECLASS" -> "true")
    val nodeclassMetaTags = Map("FOOT1" -> "BAR", "BAZT1" -> "BAAAAZ")
    val assetTag = "nodeclasstest"
    val assetStatus = Status.Allocated.get
    val assetType = AssetType.ServerNode.get
    val similarAssetTag = "similar_asset"
    val similarAssetData = List[(String, Status, Map[String,String])](
      (similarAssetTag,Status.Unallocated.get,nodeclassMetaTags),
      ("not_similar",Status.Unallocated.get,Map[String,String]()),
      ("similar_not_unallocated", Status.Provisioned.get,nodeclassMetaTags)
    )
  }



}
