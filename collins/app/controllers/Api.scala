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

  case class ResponseData(status: Status, data: Map[String,String])

  protected implicit val securitySpec = SecuritySpec(isSecure = true, Seq("infra"))
  protected val defaultOutputType = JsonOutput()

  protected def formatResponseData(response: ResponseData)(implicit req: Request[AnyContent]) = {
    getOutputType(req) match {
      case o: TextOutput =>
        response.status(response.data.mkString("\n")).as(o.contentType)
      case o: BashOutput =>
        response.status(response.data.map { case(k, v) =>
          "%s=%s".format(k,v)
        }.mkString("\n") + "\n").as(o.contentType)
      case o: JsonOutput =>
        val jsonMap = JsObject(response.data.map { case(k, v) =>
          (k -> JsString(v))
        })
        response.status(stringify(jsonMap)).as(o.contentType)
    }
  }

  protected def getOutputType(request: Request[AnyContent]): OutputType = {
    OutputType(request) match {
      case Some(ot) => ot
      case None => defaultOutputType
    }
  }
}
