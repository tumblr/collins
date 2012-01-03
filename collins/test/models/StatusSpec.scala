package models

import test.ApplicationSpecification

import org.specs2._
import specification._

class StatusSpec extends ApplicationSpecification {
  
  "Status Model Specification".title

  args(sequential = true)

  "The Status Model" should {

    "Handle validation" in {
      "Disallow empty names" in {
        Status("", "Description") must throwA[IllegalArgumentException]
      }
      "Disallow empty descriptions" in {
        Status("Name", "") must throwA[IllegalArgumentException]
      }
    }

    "Support find methods" in {
      "findById" in {
        Status.findById(1) must beSome[Status]
        Status.findById(0) must beNone
      }
      "findByName" in {
        Status.findByName(Status.Enum.New.toString) must beSome[Status]
        Status.findByName("fizzbuzz") must beNone
      }
    }

    "Support CRUD Operations" in {
      val status = Status("test1", "New test")

      "CREATE" in {
        Model.withConnection { implicit con =>
          val created = Status.create(status)
          created.id.isDefined must beTrue
        }
      }

      "READ" in {
        Model.withConnection { implicit con =>
          val _test1 = Status.findByName(status.name)
          _test1 must beSome[Status]
          val test1 = _test1.get
          test1.getId must be_>=(5)
          test1.name mustEqual status.name
          test1.description mustEqual status.description
        }
      }

      "UPDATE" in {
        Model.withConnection { implicit con =>
          val test = Status.findByName(status.name).get
          Status.update(test.copy(description = "updated test")) mustEqual 1
          Status.find("name={name}").on('name -> status.name).single().description mustEqual "updated test"
        }
      }

      "DELETE" in {
        Model.withConnection { implicit con =>
          val testId = Status.findByName(status.name).get.getId
          val query = Status.delete("id={id}").on('id -> testId).executeUpdate() mustEqual 1
          Status.findById(testId) must beNone
        }
      }
    }
  }

}
