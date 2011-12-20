package util

import play.api.mvc._
import play.api.http.HeaderNames

sealed trait OutputType {
  val fileExtension: String
  val queryString: (String,String)
  val contentType: String

  def inPath(request: Request[AnyContent]) = request.path.endsWith(fileExtension)
  def inQueryString(request: Request[AnyContent]) = checkQueryString(request.queryString)
  def inHeader(request: Request[AnyContent]) = {
    request.headers.get(HeaderNames.ACCEPT).map { header =>
      header.contains(contentType)
    }.getOrElse(false)
  }
  def inBody(request: Request[AnyContent]) = {
    request.body match {
      case AnyContentAsUrlFormEncoded(data) =>
        checkQueryString(data)
      case _ => false
    }
  }

  def matches(req: Request[AnyContent]): Boolean = {
    inPath(req) || inQueryString(req) || inHeader(req) || inBody(req)
  }
  protected def checkQueryString(data: Map[String, Seq[String]]) = {
    data.get(queryString._1).map { a =>
      a.headOption.map { _.equals(queryString._2) }.getOrElse(false)
    }.getOrElse(false)
  }
}

object OutputType {
  def apply(request: Request[AnyContent]): Option[OutputType] = request match {
    case html if HtmlOutput().matches(html) => Some(HtmlOutput())
    case json if JsonOutput().matches(json) => Some(JsonOutput())
    case bash if BashOutput().matches(bash) => Some(BashOutput())
    case text if TextOutput().matches(text) => Some(TextOutput())
    case _ => None
  }
  def isHtml(request: Request[AnyContent]): Boolean = HtmlOutput().matches(request)
}

case class JsonOutput() extends OutputType {
  val fileExtension = ".json"
  val queryString = "outputType" -> "json"
  val contentType = "application/json"
  override def toString() = "JsonOutput"
}

case class HtmlOutput() extends OutputType {
  val fileExtension = ".html"
  val queryString = "outputType" -> "html"
  val contentType = "text/html"
  override def toString() = "HtmlOutput"
}

case class BashOutput() extends OutputType {
  val fileExtension = ".sh"
  val queryString = "outputType" -> "sh"
  val contentType = "text/x-shellscript"
  override def toString() = "BashOutput"
}

case class TextOutput() extends OutputType {
  val fileExtension = ".txt"
  val queryString = "outputType" -> "text"
  val contentType = "text/plain"
  override def toString() = "TextOutput"
}
