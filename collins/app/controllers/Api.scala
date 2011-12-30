package controllers

import models.Asset
import util._

import play.api._
import play.api.data._
import play.api.libs.json._
import play.api.mvc._
import java.io.File
import java.util.Date

private[controllers] case class ResponseData(status: Results.Status, data: JsObject, headers: Seq[(String,String)] = Nil, attachment: Option[AnyRef] = None) {
  def map[T](fn: ResponseData => T) = fn(this)
}

trait ApiResponse extends Controller {
  protected val defaultOutputType = JsonOutput()

  private def contentTypeWithCharset(o: OutputType) = {
    o.contentType + "; charset=utf-8"
  }

  protected def formatResponseData(response: ResponseData)(implicit req: Request[AnyContent]) = {
    getOutputType(req) match {
      case o: TextOutput =>
        response.status(formatTextResponse(response.data) + "\n").as(contentTypeWithCharset(o)).withHeaders(response.headers:_*)
      case o: BashOutput =>
        response.status(formatBashResponse(response.data) + "\n").as(contentTypeWithCharset(o)).withHeaders(response.headers:_*)
      case o: JsonOutput =>
        response.status(Json.stringify(response.data)).as(contentTypeWithCharset(o)).withHeaders(response.headers:_*)
      case o: HtmlOutput =>
        val e = new Exception("Unhandled view")
        e.printStackTrace()
        throw e
    }
  }

  protected def formatBashResponse(jsobject: JsObject, prefix: String = ""): String = {
    def formatBasic(jsvalue: JsValue): String = {
      jsvalue match {
        case JsNull => ""
        case JsUndefined(error) => "\"%s\"".format(error)
        case JsBoolean(value) => value match {
          case true => "true"
          case false => "false"
        }
        case JsNumber(number) => number.toString
        case JsString(s) => "\"%s\"".format(s)
        case _ => throw new IllegalArgumentException("Unsupported JS type")
      }
    }
    def formatList(jsvalue: List[JsValue], listPrefix: String = ""): String = {
      val isObj = jsvalue.find { item => item.isInstanceOf[JsObject] }.map { _ => true }.getOrElse(false)
      val isNonPrim = jsvalue.find { item =>
        item.isInstanceOf[JsObject] || item.isInstanceOf[JsArray]
      }.map { _ => true }.getOrElse(false)

      if (isObj) {
        jsvalue.zipWithIndex.map { case(item,id) =>
          item match {
            case o: JsObject => formatBashResponse(o, listPrefix + id.toString + "_") + "\n"
            case b => formatBasic(b)
          }
        }.mkString("")
      } else if (!isNonPrim) {
        listPrefix + "=" + jsvalue.map { i => formatBasic(i) }.mkString(",") + ";"
      } else {
        throw new Exception("Invalid JS specified")
      }
    }
    jsobject.value.map { case(k, v) =>
      v match {
        case m: JsObject => formatBashResponse(m, "%s_".format(prefix + k))
        case JsArray(list) => formatList(list, "%s_".format(prefix + k))
        case o => "%s%s=%s;".format(prefix, k, formatBasic(o))
      }
    }.mkString("\n")
  }

  protected def formatTextResponse(jsobject: JsObject, depth: Int = 0): String = {
    def formatBasic(jsvalue: JsValue): String = {
      jsvalue match {
        case JsNull => "null"
        case JsUndefined(error) => error
        case JsBoolean(value) => value.toString
        case JsNumber(number) => number.toString
        case JsString(s) => s
        case _ => throw new IllegalArgumentException("Unsupported JS type")
      }
    }
    def formatList(jsvalue: List[JsValue]): String = {
      jsvalue.map { item =>
        item match {
          case JsArray(list) => formatList(list)
          case o: JsObject => "\n" + formatTextResponse(o, depth + 1)
          case b => formatBasic(b)
        }
      }.mkString(",")
    }
    val prefix = if (depth > 0) { "\t" * depth } else { "" }
    jsobject.value.map { case(k, v) =>
      prefix + k + "\t" + (v match {
        case m: JsObject => "\n" + formatTextResponse(m, depth + 1)
        case JsArray(list) => formatList(list)
        case o => formatBasic(v)
      })
    }.mkString("\n")
  }


  protected def getOutputType(request: Request[AnyContent]): OutputType = {
    OutputType(request) match {
      case Some(ot) => ot
      case None => defaultOutputType
    }
  }


}

trait Api extends ApiResponse with AssetApi with AssetLogApi {
  this: SecureController =>

  protected implicit val securitySpec = SecuritySpec(isSecure = true, Nil)

  def ping = Action { implicit req =>
    formatResponseData(ResponseData(Results.Ok, JsObject(Map(
      "Data" -> JsObject(Map(
        "Timestamp" -> JsString(Helpers.dateFormat(new Date())),
        "TestObj" -> JsObject(Map(
          "TestString" -> JsString("test"),
          "TestList" -> JsArray(List(JsNumber(1), JsNumber(2)))
        )),
        "TestList" -> JsArray(List(
          JsObject(Map("id" -> JsNumber(123), "name" -> JsString("foo123"))),
          JsObject(Map("id" -> JsNumber(124), "name" -> JsString("foo124"))),
          JsObject(Map("id" -> JsNumber(124), "name" -> JsString("foo124")))
        ))
      )),
      "Status" -> JsString("Ok")
    ))))
  }
}

object Api {
  def withAssetFromTag[T](tag: String)(f: Asset => Either[ResponseData,T]): Either[ResponseData,T] = {
    Asset.isValidTag(tag) match {
      case false => Left(getErrorMessage("Invalid tag specified"))
      case true => Asset.findByTag(tag) match {
        case Some(asset) => f(asset)
        case None => Left(getErrorMessage("Could not find specified asset", Results.NotFound))
      }
    }
  }

  def getErrorMessage(msg: String, status: Results.Status = Results.BadRequest) = {
    ResponseData(status, JsObject(Map("ERROR_DETAILS" -> JsString(msg))))
  }

}
