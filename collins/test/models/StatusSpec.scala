package models

import org.specs2.mutable._
import anorm._
import anorm.defaults._

class StatusSpec extends DatabaseSpec {
  "Status" should {
    "create and retrieve a status" >> {
      val status = Status("test1", "New test")
      Model.withConnection { implicit con =>
        val created = Status.create(status)
        created.id.toOption must beSome
        val _test1 = Status.find("name={name}").on('name -> "test1").first()
        _test1 must beSome
        val test1 = _test1.get
        test1.getId must be_>=(5)
        test1.name mustEqual "test1"
        test1.description mustEqual "New test"
      }
    }
  }
}
