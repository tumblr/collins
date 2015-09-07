package collins.models

import org.specs2._
import specification._
import play.api.test.WithApplication

class StatusSpec extends mutable.Specification {

  "Status Model Specification".title

  args(sequential = true)

  "The Status Model" should {

    "Handle validation" in new WithApplication {
      "Disallow empty names" in {
        Status("", "Description").validate() must throwA[IllegalArgumentException]
        Status.create(Status("", "Description")) must throwA[IllegalArgumentException]
      }
      "Disallow empty descriptions" in {
        Status("Name", "").validate() must throwA[IllegalArgumentException]
        Status.update(Status("Name", "")) mustEqual 0
      }
    }

    "Support find methods" in new WithApplication {
      "findById" in {
        Status.findById(1) must beSome[Status]
        Status.findById(0) must beNone
      }
      "findByName" in {
        Status.findByName("New") must beSome[Status]
        Status.findByName("fizzbuzz") must beNone
      }
    }

    "Support CRUD Operations" in new WithApplication {
      val status = Status("test1", "New test")

      "CREATE" in {
        val created = Status.create(status)
        created.id must be_>(0)
      }

      "READ" in {
        val _test1 = Status.findByName(status.name)
        _test1 must beSome[Status]
        val test1 = _test1.get
        test1.id must be_>=(5)
        test1.name mustEqual status.name
        test1.description mustEqual status.description
      }

      "UPDATE" in {
        val test = Status.findByName(status.name).get
        Status.update(test.copy(description = "updated test")) mustEqual 1
        Status.findByName(status.name).get.description mustEqual "updated test"
      }

      "DELETE" in {
        val testStatus = Status.findByName(status.name).get
        val query = Status.delete(testStatus) mustEqual 1
        Status.findById(testStatus.id) must beNone
      }
    }
  }

}
