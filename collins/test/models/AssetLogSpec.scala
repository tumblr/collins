package models

import test.ApplicationSpecification

import org.specs2._
import specification._

class AssetLogSpec extends ApplicationSpecification {

  "AssetLog Model Specification".title

  args(sequential = true)

  "The AssetLog Model" should {

    "CREATE" in new mocklog {
      val result = Model.withConnection { implicit con => AssetLog.create(newLog) }
      result.id.isDefined must beTrue
      result.getId must beGreaterThan(1L)
    }

    "Support getters/finders" in {

      "find with an asset" in new concretelog {
        AssetLog.list(Some(asset)).total mustEqual 2
      }

      "find with a filter and no asset" in new concretelog {
        val alert = AssetLog.list(None,0,10,"DESC","Alert")
        alert.total mustEqual 1
        alert.items(0).isAlert must beTrue
        val info = AssetLog.list(None,0,10,"DESC","Informational")
        info.total mustEqual 1
        info.items(0).isInformational must beTrue
      }

      "find with a negating filter and no asset" in new concretelog {
        val alert = AssetLog.list(None,0,10,"DESC","!Informational")
        alert.total mustEqual 1
        alert.items(0).isAlert must beTrue
        val info = AssetLog.list(None,0,10,"DESC","!Alert")
        info.total mustEqual 1
        info.items(0).isInformational must beTrue
      }

      "find with a sort" in {
        val desc = AssetLog.list(None, 0, 10, "DESC")
        desc.items(0).getId must be_>(desc.items(1).getId)
        val asc = AssetLog.list(None, 0, 10, "ASC")
        asc.items(0).getId must be_<(asc.items(1).getId)
      }

    } // support getters/finders
  } // Asset should

  trait mocklog extends Scope {
    val asset = Asset.findById(1).get
    val msg = "Hello World"
    val format = AssetLog.Formats.PlainText
    val source = AssetLog.Sources.Internal
    val newLog = AssetLog.alert(asset, msg, format, source)
  }

  trait concretelog extends Scope {
    val id = 1
    val asset_id = 1
    val format = 0
    val source = 0
    val message_type = 6 // Informational
    val asset = Asset.findById(1).get
    val message = "Automatically created by database migration"
  }


}
