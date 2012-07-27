package models

import test.ApplicationSpecification

import org.specs2._
import specification._

class AssetMetaSpec extends ApplicationSpecification {

  "AssetMeta Model Specification".title

  args(sequential = true)

  "The AssetMeta Model" should {

    "Handle validation" in {
      "Disallow empty or bad names" in {
        val ams = Seq(
          AssetMeta("", -1, "Foo", "Description"),
          AssetMeta("Foo", -1, "Foo", "Description"),
          AssetMeta("Foo Bar", -1, "Foo", "Description")
        )
        ams.foreach { am =>
          am.validate() must throwA[IllegalArgumentException]
          AssetMeta.create(am) must throwA[IllegalArgumentException]
        }
        true
      }
      "Disallow empty descriptions" in {
        val am = AssetMeta("FOO", -1, "Label", "")
        am.validate() must throwA[IllegalArgumentException]
        AssetMeta.update(am) mustEqual 0
      }
    }

    "Support CRUD Operations" in {

      "CREATE" in new mockassetmeta {
        val result = AssetMeta.create(newMeta)
        result.id must beGreaterThan(1L)
      }

      "UPDATE" in new mockassetmeta {
        val maybeMeta = AssetMeta.findByName(metaName)
        maybeMeta must beSome[AssetMeta]
        val realMeta = maybeMeta.get
        realMeta.priority mustEqual -1
        AssetMeta.update(realMeta.copy(priority = 1))
        AssetMeta.findByName(metaName).map { a =>
          a.priority mustEqual 1
        }.getOrElse(failure("Couldn't find asset meta but expected to"))
      }

      "DELETE" in new mockassetmeta {
        AssetMeta.findByName(metaName).map { a =>
          AssetMeta.delete(a) mustEqual 1
          AssetMeta.findById(a.getId) must beNone
        }.getOrElse(failure("Couldn't find asset meta but expected to"))
      }
    }

    "Support getters/finders" in {

      "findByTag" in new concreteassetmeta {
        AssetMeta.findByName(metaName) must beSome[AssetMeta]
      }
      
      "findAll" in {
        AssetMeta.findAll().size must be_>=(AssetMeta.Enum.values.size)
      }

    } // support getters/finders

    "Support value typeing enforcement" in {
      "create typed meta" in new numberassetmeta {
        val result = AssetMeta.create(newMeta)
        result.id must beGreaterThan(1L)
      }
      "allow numeric value" in new numberassetmeta {
        val maybeMeta = AssetMeta.findByName(metaName)
        maybeMeta must beSome[AssetMeta]
        val realMeta = maybeMeta.get
        val whateverAsset = Asset("foo",Status.Enum.Allocated,AssetType.Enum.ServerNode)
        AssetMetaValue(whateverAsset, realMeta.id, "123") 
        success
      }
      "reject non-numeric value" in new numberassetmeta {
        val maybeMeta = AssetMeta.findByName(metaName)
        maybeMeta must beSome[AssetMeta]
        val realMeta = maybeMeta.get
        val whateverAsset = Asset("foo",Status.Enum.Allocated,AssetType.Enum.ServerNode)
        AssetMetaValue(whateverAsset, realMeta.id, "a123") must throwA[IllegalArgumentException]

      }

      

    }
  } // Asset should

  trait mockassetmeta extends Scope {
    val metaName = "TESTING123"
    val metaPriority = -1
    val metaLabel = "Testing"
    val metaDescription = "Tag for testing"
    val newMeta = AssetMeta(metaName, metaPriority, metaLabel, metaDescription)
  }

  trait concreteassetmeta extends Scope {
    val metaName = "SERVICE_TAG"
    val metaPriority = 1
    val metaLabel = "Service Tag"
    val metaDescription = "Vendor supplied service tag"
  }

  trait numberassetmeta extends Scope {
    val metaName = "NUMERIC"
    val metaPriority = -1
    val metaLabel = "Numeric"
    val metaDescription = "Numeric tag for testing"
    val metaType = AssetMeta.ValueType.Integer.id
    val newMeta = AssetMeta(metaName, metaPriority, metaLabel, metaDescription, 0, metaType)
  }


}
