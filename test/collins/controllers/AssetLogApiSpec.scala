package collins.controllers

import collins._
import org.specs2._
import specification._
import play.api.test.WithApplication

class AssetLogApiSpec extends mutable.Specification with ControllerSpec {

  "Asset Log API specification".title

  args(sequential = true)

  val user = getLoggedInUser("infra")
  val api = getApi(user)

  "The Log API" should {
    "When Creating logs" in new WithApplication {
	    "Support creating logs" in new log {
	      val reqCreate = FakeRequest("PUT", assetCreateLogUrl)
	      val resCreate = Extract.from(api.submitLogData(assetId).apply(reqCreate))
	      resCreate must haveStatus(201)
	      resCreate must haveJsonData.which { s =>
	        s must /("data") */("SUCCESS"   -> true)
	        s must /("data") */("ID"        -> logId)
	        s must /("data") */("ASSET_TAG" -> assetId)
	        s must /("data") */("MESSAGE"   -> "User test: %s".format(message))
	      }
	    }
    }

    "When querying logs" in new WithApplication {
	    "Support get by id" in new log {
	      val reqGet = FakeRequest("GET", logGetUrl)
	      val resGet = Extract.from(api.getLogData(logId).apply(reqGet))
	      resGet must haveStatus(200)
	      resGet must haveJsonData.which { s =>
	        s must /("data") */("SUCCESS"   -> true)
	        s must /("data") */("ID"        -> logId)
	        s must /("data") */("ASSET_TAG" -> assetId)
	        s must /("data") */("MESSAGE"   -> "User test: %s".format(message))
	      }
	    }


	    "Support getting all logs" in new log {
	      val reqAll = FakeRequest("GET", "/api/assets/logs")
	      val resAll = Extract.from(api.getAllLogData(0, 10, "DESC", "").apply(reqAll))
	      resAll must haveStatus(200)
	    }
	
	    "Support getting logs for asset" in new log {
	      val reqAsset = FakeRequest("GET", assetGetLogUrl)
	      val resAsset = Extract.from(api.getAssetLogData(assetId, 0, 10, "DESC", "").apply(reqAsset))
	      resAsset must haveStatus(200)
	    }
    }
  }

  trait log extends Scope with ResponseMatchHelpers {
    val logId              = 2
    val assetId            = "tumblrtag1"
    val message            = "hello world"
    val logGetUrl          = "/api/log/%s".format(logId)
    val assetGetLogUrl     = "/api/assets/%s/logs".format(assetId)
    val assetCreateLogUrl  = "/api/assets/%s/log?message=%s".format(assetId, message)
  }

}
