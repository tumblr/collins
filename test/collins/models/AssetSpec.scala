package collins.models

import shared.PageParams
import org.specs2._
import specification._
import play.api.test.WithApplication
import play.api.test.FakeApplication

class AssetSpec extends mutable.Specification {

  "Asset Model Specification".title

  args(sequential = true)

  "The Asset Model" should {

    "Support CRUD Operations" in new WithApplication {

      val assetTag = "tumblrtag2"
      val assetStatus = Status.Incomplete.get
      val assetType = AssetType.ServerNode.get
      val newAsset = Asset(assetTag, assetStatus, assetType)

      val result = Asset.create(newAsset)
      result.id must beGreaterThan(1L)

      val maybeAsset = Asset.findByTag(assetTag)
      maybeAsset must beSome[Asset]
      val realAsset = maybeAsset.get
      Asset.update(realAsset.copy(statusId = Status.New.get.id))
      Asset.findByTag(assetTag).map { a =>
        a.status.id mustEqual (Status.New.get.id)
      }.getOrElse(failure("Couldn't find asset but expected to"))

      Asset.findByTag(assetTag).map { a =>
        Asset.delete(a) mustEqual 1
        Asset.findById(a.id) must beNone
      }.getOrElse(failure("Couldn't find asset but expected to"))
    }

    "Support getters/finders" in new WithApplication {

      val assetTag = "tumblrtag1"
      val assetStatus = Status.Incomplete.get
      val assetType = AssetType.ServerNode.get
      val assetId = 1

      Asset.findByTag(assetTag) must beSome[Asset]
      Asset.findByTag(assetTag).get.tag mustEqual assetTag

      val maybeAsset = Asset.findByTag(assetTag)
      maybeAsset must beSome[Asset]
      val asset = maybeAsset.get
      val attributes = asset.getAllAttributes
      attributes.ipmi must beSome.which { ipmi =>
        ipmi.dottedAddress mustEqual "10.0.0.2"
        ipmi.dottedGateway mustEqual "10.0.0.1"
      }

    } // support getters/finders
  } // Asset should

}
