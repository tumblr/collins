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
        Asset.update(realAsset.copy(status = Status.Enum.New.id))
        Asset.findByTag(assetTag).map { a =>
          a.getStatus().getId mustEqual(Status.Enum.New.id)
        }.getOrElse(failure("Couldn't find asset but expected to"))
      }

      "DELETE" in new mockasset {
        Asset.findByTag(assetTag).map { a =>
          Asset.delete(a) mustEqual 1
          Asset.findById(a.getId) must beNone
        }.getOrElse(failure("Couldn't find asset but expected to"))
      }
    }

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
    val assetStatus = Status.Enum.Incomplete
    val assetType = AssetType.Enum.ServerNode
    val newAsset = Asset(assetTag, assetStatus, assetType)
  }

  trait concreteasset extends Scope {
    val assetTag = "tumblrtag1"
    val assetStatus = Status.Enum.Incomplete
    val assetType = AssetType.Enum.ServerNode
    val assetId = 1
  }


}
