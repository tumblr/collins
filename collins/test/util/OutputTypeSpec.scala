package util

import org.specs2.mutable._
import play.api.mvc._

class OutputTypeSpec extends Specification with controllers.SpecHelpers {

  def isJson(o: OutputType) = o must haveClass[JsonOutput]
  def isBash(o: OutputType) = o must haveClass[BashOutput]
  def isText(o: OutputType) = o must haveClass[TextOutput]
  def isHtml(o: OutputType) = o must haveClass[HtmlOutput]

  "Output Types" should {
    "Be detected for" >> {

      "JSON" >> {
        "Based on Path" >> {
          val req1 = getRequest(MockRequest(path = "/api/fizz.json"))
          OutputType(req1) must beSome.which(isJson)
        }
        "Based on Query String" >> {
          val req1 = getRequest(MockRequest(queryString = Map("outputType" -> Seq("json"))))
          OutputType(req1) must beSome.which(isJson)
        }
        "Based on Headers" >> {
          val req1 = getRequest(MockRequest(headers = Seq("application/json")))
          OutputType(req1) must beSome.which(isJson)
        }
        "Based on Request Body" >> {
          val body = AnyContentAsUrlFormEncoded(Map("outputType" -> Seq("json")))
          val req1 = getRequest(MockRequest(body = body))
          OutputType(req1) must beSome.which(isJson)
        }
      } // JSON

      "Bash" >> {
        "Based on Path" >> {
          val req1 = getRequest(MockRequest(path = "/api/fizz.sh"))
          OutputType(req1) must beSome.which(isBash)
        }
        "Based on Query String" >> {
          val req1 = getRequest(MockRequest(queryString = Map("outputType" -> Seq("sh"))))
          OutputType(req1) must beSome.which(isBash)
        }
        "Based on Headers" >> {
          val req1 = getRequest(MockRequest(headers = Seq("text/x-shellscript")))
          OutputType(req1) must beSome.which(isBash)
        }
        "Based on Request Body" >> {
          val body = AnyContentAsUrlFormEncoded(Map("outputType" -> Seq("sh")))
          val req1 = getRequest(MockRequest(body = body))
          OutputType(req1) must beSome.which(isBash)
        }
      } // Bash

      "Text" >> {
        "Based on Path" >> {
          val req1 = getRequest(MockRequest(path = "/api/fizz.txt"))
          OutputType(req1) must beSome.which(isText)
        }
        "Based on Query String" >> {
          val req1 = getRequest(MockRequest(queryString = Map("outputType" -> Seq("text"))))
          OutputType(req1) must beSome.which(isText)
        }
        "Based on Headers" >> {
          val req1 = getRequest(MockRequest(headers = Seq("text/plain")))
          OutputType(req1) must beSome.which(isText)
        }
        "Based on Request Body" >> {
          val body = AnyContentAsUrlFormEncoded(Map("outputType" -> Seq("text")))
          val req1 = getRequest(MockRequest(body = body))
          OutputType(req1) must beSome.which(isText)
        }
      } // Text

      "HTML" >> {
        "Based on Path" >> {
          val req1 = getRequest(MockRequest(path = "/api/fizz.html"))
          OutputType(req1) must beSome.which(isHtml)
        }
        "Based on Query String" >> {
          val req1 = getRequest(MockRequest(queryString = Map("outputType" -> Seq("html"))))
          OutputType(req1) must beSome.which(isHtml)
        }
        "Based on Headers" >> {
          val req1 = getRequest(MockRequest(headers = Seq("text/html")))
          OutputType(req1) must beSome.which(isHtml)
        }
        "Based on Request Body" >> {
          val body = AnyContentAsUrlFormEncoded(Map("outputType" -> Seq("html")))
          val req1 = getRequest(MockRequest(body = body))
          OutputType(req1) must beSome.which(isHtml)
        }
      } // HTML

    } // Be detected for

    "Not be detected for" >> {
      "XML and other unsupported formats" >> {
        "Based on Path" >> {
          val req1 = getRequest(MockRequest(path = "/api/fizz.xml"))
          OutputType(req1) must beNone
        }
        "Based on Query String" >> {
          val req1 = getRequest(MockRequest(queryString = Map("outputType" -> Seq("xml"))))
          OutputType(req1) must beNone
        }
        "Based on Headers" >> {
          val req1 = getRequest(MockRequest(headers = Seq("text/xml")))
          OutputType(req1) must beNone
        }
        "Based on Request Body" >> {
          val body = AnyContentAsUrlFormEncoded(Map("outputType" -> Seq("xml")))
          val req1 = getRequest(MockRequest(body = body))
          OutputType(req1) must beNone
        }
      }
    }
  } // Output types should

}
