package collins.controllers

import org.specs2.mutable

import play.api.test.FakeApplication
import play.api.test.WithApplication

import collins.FakeRequest

class AssetApiFindSpec extends mutable.Specification {

  "Asset API Find Specification".title

  args(sequential = true)

  "The REST API" should {

    /*
     * NOTE (DS) - If these tests suddenly start failing, try clearing the Solr index by shutting down solr and deleting the SOLR_HOME/data
     * directory
     */
    "Support find" in {
      "by custom attribute" in new WithApplication(FakeApplication(
        additionalConfiguration = Map(
          "solr.enabled" -> true,
          "solr.repopulateOnStartup" -> true))) with AssetApiHelper {
        override val assetTag = "testA8712"
        createAsset() must haveStatus(201)
        updateHwInfo() must haveStatus(200)
        updateAttributes() must haveStatus(200)
        waitOnCallbacks()

        val req = FakeRequest("GET", findUrl + "?attribute=foo;bar")
        val result = Extract.from(api.getAssets(0, 10, "", "TAG", "").apply(req))
        result must haveJsonData.which { txt =>
          txt must /("data") */ ("Data") */ ("TAG" -> assetTag)
        }
        val req2 = FakeRequest("GET", findUrl + "?attribute=fizz;buzz")
        Extract.from(api.getAssets(0, 10, "", "TAG", "").apply(req2)) must haveJsonData.which { txt =>
          txt must /("data") */ ("Data") */ ("TAG" -> assetTag)
        }
      }
      "by type" in new WithApplication(FakeApplication(
        additionalConfiguration = Map(
          "solr.enabled" -> true,
          "solr.repopulateOnStartup" -> true))) with AssetApiHelper {
        override val assetTag = "testA8713"
        createAsset() must haveStatus(201)
        updateHwInfo() must haveStatus(200)
        updateAttributes() must haveStatus(200)
        waitOnCallbacks()

        val req = FakeRequest("GET", findUrl + "?type=SERVER_NODE")
        val result = Extract.from(api.getAssets(0, 10, "", "TAG", "").apply(req))
        result must haveJsonData.which { txt =>
          txt must /("data") */ ("Data") */ ("TAG" -> assetTag)
        }
      }
      "by status" in new WithApplication(FakeApplication(
        additionalConfiguration = Map(
          "solr.enabled" -> true,
          "solr.repopulateOnStartup" -> true))) with AssetApiHelper {
        override val assetTag = "testA8714"
        createAsset() must haveStatus(201)
        updateHwInfo() must haveStatus(200)
        updateAttributes() must haveStatus(200)
        waitOnCallbacks()

        val req = FakeRequest("GET", findUrl + "?status=Unallocated")
        val result = Extract.from(api.getAssets(0, 10, "", "TAG", "").apply(req))
        result must haveJsonData.which { txt =>
          txt must /("data") */ ("Data") */ ("TAG" -> assetTag)
        }
      }
      "by createdAfter" in new WithApplication(FakeApplication(
        additionalConfiguration = Map(
          "solr.enabled" -> true,
          "solr.repopulateOnStartup" -> true))) with AssetApiHelper {

        override val assetTag = "testA8715"
        createAsset() must haveStatus(201)
        updateHwInfo() must haveStatus(200)
        updateAttributes() must haveStatus(200)
        waitOnCallbacks()

        val req = FakeRequest("GET", findUrl + "?createdAfter=2011-12-30T00:00:00")
        Extract.from(api.getAssets(0, 10, "", "TAG", "").apply(req)) must haveJsonData.which { txt =>
          txt must /("data") */ ("Data") */ ("TAG" -> assetTag)
        }
        val req2 = FakeRequest("GET", findUrl + "?createdAfter=2020-12-30T00:00:00")
        Extract.from(api.getAssets(0, 10, "", "TAG", "").apply(req2)) must haveJsonData.which { txt =>
          txt must /("data") */ ("Pagination") */ ("TotalResults" -> 0.0)
        }
      }
      "by createdBefore" in new WithApplication(FakeApplication(
        additionalConfiguration = Map(
          "solr.enabled" -> true,
          "solr.repopulateOnStartup" -> true))) with AssetApiHelper {

        override val assetTag = "testA8716"
        createAsset()
        updateHwInfo()
        updateAttributes()
        waitOnCallbacks()

        val req = FakeRequest("GET", findUrl + "?createdBefore=2020-12-30T00:00:00")
        Extract.from(api.getAssets(0, 10, "", "TAG", "").apply(req)) must haveJsonData.which { txt =>
          txt must /("data") */ ("Data") */ ("TAG" -> assetTag)
        }
        val req2 = FakeRequest("GET", findUrl + "?createdBefore=2011-12-30T00:00:00")
        Extract.from(api.getAssets(0, 10, "", "TAG", "").apply(req2)) must haveJsonData.which { txt =>
          txt must /("data") */ ("Pagination") */ ("TotalResults" -> 0.0)
        }
      }
      "by updatedAfter" in new WithApplication(FakeApplication(
        additionalConfiguration = Map(
          "solr.enabled" -> true,
          "solr.repopulateOnStartup" -> true))) with AssetApiHelper {

        override val assetTag = "testA8717"
        createAsset() must haveStatus(201)
        updateHwInfo() must haveStatus(200)
        updateAttributes() must haveStatus(200)
        waitOnCallbacks()

        val req = FakeRequest("GET", findUrl + "?updatedAfter=2011-12-30T00:00:00")
        Extract.from(api.getAssets(0, 10, "", "TAG", "").apply(req)) must haveJsonData.which { txt =>
          txt must /("data") */ ("Data") */ ("TAG" -> assetTag)
        }
        val req2 = FakeRequest("GET", findUrl + "?updatedAfter=2020-12-30T00:00:00")
        Extract.from(api.getAssets(0, 10, "", "TAG", "").apply(req2)) must haveJsonData.which { txt =>
          txt must /("data") */ ("Pagination") */ ("TotalResults" -> 0.0)
        }
      }
      "by updatedBefore" in new WithApplication(FakeApplication(
        additionalConfiguration = Map(
          "solr.enabled" -> true,
          "solr.repopulateOnStartup" -> true))) with AssetApiHelper {

        override val assetTag = "testA8718"
        createAsset() must haveStatus(201)
        updateHwInfo() must haveStatus(200)
        updateAttributes() must haveStatus(200)
        waitOnCallbacks()

        val req = FakeRequest("GET", findUrl + "?updatedBefore=2020-12-30T00:00:00")
        Extract.from(api.getAssets(0, 10, "", "TAG", "").apply(req)) must haveJsonData.which { txt =>
          txt must /("data") */ ("Data") */ ("TAG" -> assetTag)
        }
        val req2 = FakeRequest("GET", findUrl + "?updatedAfter=2020-12-30T00:00:00")
        Extract.from(api.getAssets(0, 10, "", "TAG", "").apply(req2)) must haveJsonData.which { txt =>
          txt must /("data") */ ("Pagination") */ ("TotalResults" -> 0.0)
        }
      }
    } // Support various find mechanisms

  } // The REST API
}
