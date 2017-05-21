package collins.controllers

import org.specs2.mutable

import play.api.libs.json.JsString
import play.api.libs.json.Json
import play.api.test.FakeApplication
import play.api.test.WithApplication

import collins.FakeRequest
import collins.ResourceFinder
import collins.models.Asset
import collins.models.AssetMeta
import collins.models.AssetMetaValue
import collins.models.Status

class DynamicEnumSpec extends mutable.Specification with ControllerSpec with ResourceFinder {

  "Dynamic Enum Specification".title

  "Should properly update asset" should {

    "Has data integrity" in {
      "Creating an asset" in new WithApplication(FakeApplication(
        additionalConfiguration = Map(
          "solr.enabled" -> false))) with AssetApiHelper {
        override val assetTag = "A0001"
        createAsset() must haveStatus(201)
        updateHwInfo() must haveStatus(200)

        val result = getAsset()
        result must haveStatus(200)
        result must haveJsonData.which { s =>
          s must /("data") */ ("HARDWARE") */ ("BASE") / ("DESCRIPTION" -> "Rack Mount Chassis")
          s must /("data") */ ("HARDWARE") */ ("BASE") / ("PRODUCT" -> "PowerEdge C6105 (N/A)")
          s must /("data") */ ("HARDWARE") */ ("BASE") / ("VENDOR" -> "Winbond Electronics")
          s must /("data") */ ("HARDWARE") */ ("BASE") / ("SERIAL" -> "FZ22YQ1")
        }
      }
      "Updating an asset" in new WithApplication(FakeApplication(
        additionalConfiguration = Map(
          "solr.enabled" -> false))) with AssetApiHelper {
        override val assetTag = "A0002"
        createAsset() must haveStatus(201)
        // Update HW info twice to ensure that we are
        // overwriting the data from the first intake
        updateHwInfo() must haveStatus(200)
        updateStatus(Status.Maintenance.get, "refresh lshw") must haveStatus(200)
        updateHwInfo("lshw-basic-dynamic-enums.xml") must haveStatus(200)

        val asset = Asset.findByTag(assetTag).get
        val atts = AssetMetaValue.findByAsset(asset)
        atts.count(_.getName() == "BASE_DESCRIPTION") mustEqual 1
        atts.count(_.getName() == "BASE_PRODUCT") mustEqual 1
        atts.count(_.getName() == "BASE_VENDOR") mustEqual 1
        atts.count(_.getName() == "BASE_SERIAL") mustEqual 1

        val result = getAsset()
        result must haveStatus(200)
        result must haveJsonData.which { s =>
          s must /("data") */ ("HARDWARE") */ ("BASE") / ("DESCRIPTION" -> "Rack Mount Chassis 2")
          s must /("data") */ ("HARDWARE") */ ("BASE") / ("PRODUCT" -> "PowerEdge C6105 2")
          s must /("data") */ ("HARDWARE") */ ("BASE") / ("VENDOR" -> "Winbond Electronics 2")
          s must /("data") */ ("HARDWARE") */ ("BASE") / ("SERIAL" -> "FZ22YQ2")
        }
      }
    }
  }
}
