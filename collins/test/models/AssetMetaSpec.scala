package models

import test.ApplicationSpecification

import org.specs2._
import specification._

class AssetMetaSpec extends ApplicationSpecification {

  "AssetMeta Model Specification".title

  args(sequential = true)

  "The AssetMeta Model" should {

    "Support CRUD Operations" in {

      "CREATE" in new mockassetmeta {
        val result = Model.withConnection { implicit con => AssetMeta.create(newMeta) }
        result.id.isDefined must beTrue
        result.getId must beGreaterThan(1L)
      }

      "UPDATE" in new mockassetmeta {
        Model.withConnection { implicit con =>
          val maybeMeta = AssetMeta.findByName(metaName)
          maybeMeta must beSome[AssetMeta]
          val realMeta = maybeMeta.get
          realMeta.priority mustEqual -1
          AssetMeta.update(realMeta.copy(priority = 1))
          AssetMeta.findByName(metaName).map { a =>
            a.priority mustEqual 1
          }.getOrElse(failure("Couldn't find asset meta but expected to"))
        }
      }

      "DELETE" in new mockassetmeta {
        Model.withConnection { implicit con =>
          AssetMeta.findByName(metaName).map { a =>
            AssetMeta.delete("id={id}").on('id -> a.getId).executeUpdate() mustEqual 1
            AssetMeta.findById(a.getId) must beNone
          }.getOrElse(failure("Couldn't find asset meta but expected to"))
        }
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


}
