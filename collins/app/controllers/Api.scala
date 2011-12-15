package controllers

import play.api._
import play.api.data._
import play.api.json._
import play.api.mvc._
import models._
import util._
import java.io.File

trait Api extends Controller with AssetApi {
  this: SecureController =>

  case class ResponseData(status: Status, data: JsObject)

  protected implicit val securitySpec = SecuritySpec(isSecure = true, Seq("infra"))
  protected val defaultOutputType = JsonOutput()

  protected def formatResponseData(response: ResponseData)(implicit req: Request[AnyContent]) = {
    getOutputType(req) match {
      case o: TextOutput =>
        response.status(response.data.value.map { case(k, v) =>
          k + "\t" + (v match {
            case JsNull => "null"
            case JsUndefined(error) => error
            case JsBoolean(value) => value.toString
            case JsNumber(number) => number.toString
            case JsString(s) => s
            case o => throw new IllegalArgumentException("Unsupported js type: " + o.getClass)
          })
        }.mkString("\n")).as(o.contentType)
      case o: BashOutput =>
        response.status(response.data.value.map { case(k, v) =>
          "%s=%s;".format(k, (v match {
            case JsNull => ""
            case JsUndefined(error) => "\"%s\"".format(error)
            case JsBoolean(value) => value match {
              case true => "1"
              case false => "0"
            }
            case JsNumber(number) => number.toString
            case JsString(s) => "\"%s\"".format(s)
            case o => throw new IllegalArgumentException("Unsupported js type: " + o.getClass)
          }))
        }.mkString("\n") + "\n").as(o.contentType)
      case o: JsonOutput =>
        response.status(stringify(response.data)).as(o.contentType)
    }
  }

  protected def getOutputType(request: Request[AnyContent]): OutputType = {
    OutputType(request) match {
      case Some(ot) => ot
      case None => defaultOutputType
    }
  }
}
