package models

import test.ApplicationHelper
import org.specs2.mutable._
import anorm._
import anorm.defaults._
import play.api.Play
import play.api.test.FakeApplication

class StatusSpec extends Specification {
  
  "Status Model Specification".title

  args(sequential = true)

  step {
    Play.start(FakeApplication())
  }

  "Status" should {

    "Support findById" in {
      Status.findById(1) must beSome
    }

    "CRUD" in {
      val status = Status("test1", "New test")

      "CREATE" in {
        Model.withConnection { implicit con =>
          val created = Status.create(status)
          created.id.isDefined must beTrue
        }
      }

      "READ" in {
        Model.withConnection { implicit con =>
          val _test1 = Status.find("name={name}").on('name -> "test1").first()
          _test1 must beSome
          val test1 = _test1.get
          test1.getId must be_>=(5)
          test1.name mustEqual "test1"
          test1.description mustEqual "New test"
        }
      }

      "UPDATE" in {
        Model.withConnection { implicit con =>
          val test = Status.find("name={name}").on('name -> "test1").single()
          Status.update(test.copy(description = "updated test")) mustEqual 1
          Status.find("name={name}").on('name -> "test1").single().description mustEqual "updated test"
        }
      }

      "DELETE" in {
        Model.withConnection { implicit con =>
          val testId = Status.find("name={name}").on('name -> "test1").single().getId
          val query = Status.delete("id={id}").on('id -> testId).executeUpdate() mustEqual 1
          Status.findById(testId) must beNone
        }
      }
    }
  }

  step {
    Play.stop()
  }

}
