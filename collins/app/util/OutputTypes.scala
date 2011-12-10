package util

import play.api.mvc._
import play.api.http.HeaderNames

sealed abstract class OutputType()
object OutputType {
  def apply(request: Request[AnyContent]): Option[OutputType] = request match {
    case JsonOutput(req) => Some(JsonOutput())
    case BashOutput(req) => Some(BashOutput())
    case TextOutput(req) => Some(TextOutput())
    case _ => None
  }
}

class JsonOutput() extends OutputType {
  override def toString() = "JsonOutput"
}
object JsonOutput extends OutputTypeExtractor {
  val fileExtension = ".json"
  val queryString = "outputType" -> "json"
  val acceptValue = "application/json"

  def apply() = new JsonOutput()
  def unapply(req: Request[AnyContent]): Option[JsonOutput] = {
    (inPath(req) || inQueryString(req) || inHeader(req) || inBody(req)) match {
      case true => Some(JsonOutput())
      case false => None
    }
  }
}

class BashOutput() extends OutputType {
  override def toString() = "BashOutput"
}
object BashOutput extends OutputTypeExtractor {
  val fileExtension = ".sh"
  val queryString = "outputType" -> "sh"
  val acceptValue = "text/x-shellscript"

  def apply() = new BashOutput()
  def unapply(req: Request[AnyContent]): Option[BashOutput] = {
    (inPath(req) || inQueryString(req) || inHeader(req) || inBody(req)) match {
      case true => Some(BashOutput())
      case false => None
    }
  }
}

class TextOutput() extends OutputType {
  override def toString() = "TextOutput"
}
object TextOutput extends OutputTypeExtractor {
  val fileExtension = ".txt"
  val queryString = "outputType" -> "text"
  val acceptValue = "text/plain"

  def apply() = new TextOutput()
  def unapply(req: Request[AnyContent]): Option[TextOutput] = {
    (inPath(req) || inQueryString(req) || inHeader(req) || inBody(req)) match {
      case true => Some(TextOutput())
      case false => None
    }
  }
}

trait OutputTypeExtractor {
  val fileExtension: String
  val queryString: (String,String) // key/value
  val acceptValue: String

  def inPath(request: Request[AnyContent]) = request.path.endsWith(fileExtension)
  def inQueryString(request: Request[AnyContent]) = checkQueryString(request.queryString)
  def inHeader(request: Request[AnyContent]) = {
    request.headers.get(HeaderNames.ACCEPT).map { header =>
      header.contains(acceptValue)
    }.getOrElse(false)
  }
  def inBody(request: Request[AnyContent]) = {
    request.body match {
      case AnyContentAsUrlFormEncoded(data) =>
        checkQueryString(data)
      case _ => false
    }
  }

  protected def checkQueryString(data: Map[String, Seq[String]]) = {
    data.get(queryString._1).map { a =>
      a.headOption.map { _.equals(queryString._2) }.getOrElse(false)
    }.getOrElse(false)
  }
}
