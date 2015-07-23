package collins.models

import collins.models.logs._
import org.specs2._
import specification._
import play.api.test.WithApplication

class AssetLogSpec extends mutable.Specification {

  "AssetLog Model Specification".title

  args(sequential = true)

  "The AssetLog Model" should {
    
    "should create" in new WithApplication {
      "scoped" in new mocklog {
        val result = AssetLog.create(newLog)
        result.id must be_>(0L)
      }
    }

    "The api should" in {
      "Support getters/finders" in {

        "find with an asset" in new concretelog {
          val logs = AssetLog.list(Some(asset))
          logs.total mustEqual 1
          logs.items(0).getFormat.id mustEqual format.id
          logs.items(0).message mustEqual message
        }

        "find with another asset" in new mocklog {
          AssetLog.create(newLog)
          val logs = AssetLog.list(Some(asset))
          logs.total mustEqual 1
          logs.items(0).getFormat mustEqual format
          logs.items(0).message mustEqual msg
        }
        
        "find with a negating filter and no asset" in new concretelog {
          AssetLog.create(newLog)
          val alert = AssetLog.list(None,0,10,"DESC","!Informational")
          alert.total mustEqual 1
          alert.items(0).isAlert must beTrue
          val info = AssetLog.list(None,0,10,"DESC","!Alert")
          info.total mustEqual 1
          info.items(0).isInformational must beTrue
        }

        "find with a filter and no asset" in new concretelog {
          AssetLog.create(newLog)
          val alert = AssetLog.list(None,0,10,"DESC","Alert")
          alert.total mustEqual 1
          alert.items(0).isAlert must beTrue
          val info = AssetLog.list(None,0,10,"DESC","Informational")
          info.total must be_>=(1L)
          info.items(0).isInformational must beTrue
        }

        "find with a filter and an asset" in new concretelog {
          AssetLog.list(Some(asset),0,10,"DESC","Alert").total mustEqual 0
          val info = AssetLog.list(Some(asset),0,10,"DESC","Informational")
          info.total mustEqual 1
          info.items(0).assetId mustEqual asset_id
        }

        "find with a negating filter and an asset" in new mocklog {
          AssetLog.create(newLog)
          val alert = AssetLog.list(Some(asset),0,10,"DESC","!Informational")
          alert.total mustEqual 1
          alert.items(0).isAlert must beTrue
          alert.items(0).assetId mustEqual asset_id
          AssetLog.list(Some(asset),0,10,"DESC","!Alert").total mustEqual 0
        }

        "find with a sort" in new concretelog {
          AssetLog.create(newLog)
          val desc = AssetLog.list(None, 0, 10, "DESC")
          desc.items(0).id must be_>(desc.items(1).id)
          val asc = AssetLog.list(None, 0, 10, "ASC")
          asc.items(0).id must be_<(asc.items(1).id)
        }

      } // support getters/finders
    }
  } // Asset should

  trait mocklog extends WithApplication {
    val tag = "tumblrtag15"
    def createAsset = {
      Asset.create(Asset(tag, Status.Incomplete.get, AssetType.ServerNode.get))
      Asset.findByTag(tag).get
    }
    def asset = Asset.findByTag(tag) match {
      case None => createAsset
      case Some(a) => a
    }
    def asset_id = asset.id
    def msg = "Hello World"
    def format = LogFormat.PlainText
    def source = LogSource.Internal
    def newLog = AssetLog.alert(asset, "tumblr", msg, format, source)
  }

  trait concretelog extends WithApplication {
    def id = 1
    def asset_id = 1L
    def format = LogFormat.PlainText
    def source = LogSource.Internal
    def message_type = 6 // Informational
    def asset = Asset.findById(1).get
    def message = "Automatically created by database migration"
    def newLog = AssetLog.alert(asset, "tumblr", "Spec error message", format, source)
  }

}
