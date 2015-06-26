package collins.controllers

import play.api.libs.json.JsArray
import play.api.libs.json.JsBoolean
import play.api.libs.json.JsNull
import play.api.libs.json.JsNumber
import play.api.libs.json.JsObject
import play.api.libs.json.JsString
import play.api.libs.json.JsUndefined
import play.api.libs.json.JsValue
import play.api.libs.json.Json
import play.api.mvc.AnyContent
import play.api.mvc.Controller
import play.api.mvc.Request
import play.api.mvc.Results
import collins.util.BashOutput
import collins.util.HtmlOutput
import collins.util.JsonOutput
import collins.util.OutputType
import collins.util.OutputType.contentTypeWithCharset
import collins.util.TextOutput
import scala.concurrent.Future
import play.api.mvc.Result

object ApiResponse extends ApiResponse {
  import OutputType.contentTypeWithCharset

  def formatJsonMessage(status: String, data: JsValue): JsObject = {
    JsObject(Seq(
      "status" -> JsString(status),
      "data" -> data
    ))
  }
  def formatJsonMessage(status: Results.Status, data: JsValue): JsObject = {
    formatJsonMessage(statusToString(status), data)
  }

  def isJsonErrorMessage(js: JsValue): Boolean = {
    (js \ "status").asOpt[String].map(e => e.contains("error")).getOrElse(false)
  }

  def formatJsonError(msg: String, ex: Option[Throwable]): JsObject = {
    val message = Seq("message" -> JsString(msg))
    val optional = ex match {
      case None => Nil
      case Some(e) =>
        val seq = formatException(e).map(s => s._1 -> JsString(s._2))
        Seq("details" -> JsObject(seq))
    }
    formatJsonMessage("error", JsObject(message ++ optional))
  }

  def bashError(msg: String, status: Results.Status = Results.BadRequest, ex: Option[Throwable]): Future[Result] = {
    val exSeq = ex.map { e => formatException(e) }.getOrElse(Seq())
    val exMsg = exSeq.map { case(k,v) =>
      """DATA_EXCEPTION_%s='%s';""".format(k.toUpperCase, v)
    }.mkString("\n")
    val output =
"""STATUS="error";
DATA_MESSAGE='%s';
%s
""".format(msg, exMsg)
    Future.successful(status(output).as(contentTypeWithCharset(BashOutput())))
  }

  def statusToString(status: Results.Status): String = {
    status.header.status match {
      case 200 => "success:ok"
      case 201 => "success:created"
      case 202 => "success:accepted"
      case ok if ok >= 200 && ok < 300 => "success:other"
      case 400 => "client_error:bad request"
      case 401 => "client_error:unauthorized"
      case 403 => "client_error:forbidden"
      case 404 => "client_error:not found"
      case 405 => "client_error:method not allowed"
      case 406 => "client_error:not acceptable"
      case 409 => "client_error:conflict"
      case userErr if userErr >= 400 && userErr < 500 => "client_error:unknown"
      case 500 => "server_error:internal server error"
      case 501 => "server_error:not implemented"
      case srvErr if srvErr >= 500 => "server_error:unknown"
      case n => "unknown:%d".format(n)
    }
  }

  def jsonError(msg: String, status: Results.Status = Results.BadRequest, ex: Option[Throwable]): Future[Result] = {
    val output: JsValue = formatJsonMessage(status, formatJsonError(msg, ex))
    Future.successful(status(output).as(contentTypeWithCharset(JsonOutput())))
  }

  def textError(msg: String, status: Results.Status = Results.BadRequest, ex: Option[Throwable]): Future[Result] = {
    val exSeq = ex.map { e => formatException(e) }.getOrElse(Seq())
    val exMsg = exSeq.map { case(k,v) => """
Exception %s  %s""".format(k, v.replace("\n","\n\t\t"))
    }.mkString("\n")
    val output =
"""Status        Error

Details
-----------------------------------------------------------------
Message       %s

%s
""".format(msg, exMsg)
    Future.successful(status(output).as(contentTypeWithCharset(TextOutput())))
  }

  private def formatException(ex: Throwable): Seq[(String,String)] = {
    Seq("classOf" -> ex.getClass.toString,
        "message" -> ex.getMessage,
        "stackTrace" -> ex.getStackTrace.map { _.toString }.mkString("\n"))
  }
}

trait ApiResponse extends Controller {
  protected val defaultOutputType = JsonOutput()

  import OutputType.contentTypeWithCharset

  def formatResponseData(response: ResponseData)(implicit req: Request[AnyContent]) = {
    getOutputType(req) match {
      case o: TextOutput =>
        response.status(formatTextResponse(response.data) + "\n").as(contentTypeWithCharset(o)).withHeaders(response.headers:_*)
      case o: BashOutput =>
        response.status(formatBashResponse(response.data) + "\n").as(contentTypeWithCharset(o)).withHeaders(response.headers:_*)
      case o: JsonOutput =>
        val rewritten = ApiResponse.isJsonErrorMessage(response.data) match {
          case true => response.data
          case false => ApiResponse.formatJsonMessage(response.status, response.data)
        }
        response.status(Json.stringify(rewritten)).as(contentTypeWithCharset(o)).withHeaders(response.headers:_*)
      case o: HtmlOutput =>
        val e = new Exception("Unhandled view")
        e.printStackTrace()
        throw e
    }
  }

  protected def formatBashResponse(jsobject: JsValue, prefix: String = ""): String = {
    def formatBasic(jsvalue: JsValue): String = {
      jsvalue match {
        case JsNull => ""
        case und: JsUndefined => "\"%s\"".format(und.error)
        case JsBoolean(value) => value match {
          case true => "true"
          case false => "false"
        }
        case JsNumber(number) => number.toString
        case JsString(s) => "\"%s\"".format(s)
        case _ => throw new IllegalArgumentException("Unsupported JS type")
      }
    }
    def formatList(jsvalue: Seq[JsValue], listPrefix: String = ""): String = {
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

    // formats a key to an acceptable POSIX environment variable name
    def formatPosixKey(key: String): String = if (!key.isEmpty) {
      val posixHeadRegex = """^[^a-zA-Z_]""".r
      val posixTailRegex = """[^a-zA-Z0-9_]""".r
      key.head.toString match {
        case posixHeadRegex() => formatPosixKey("_" + key)
        case _                => posixTailRegex.replaceAllIn(key,"_").toUpperCase
      }
    } else {
      throw new Exception("Cannot convert an empty key into a POSIX environment variable name")
    }

    // FIXME
    require(jsobject.isInstanceOf[JsObject], "Required a JsObject")
    jsobject.asInstanceOf[JsObject].value.map { case(k, v) =>
      v match {
        case m: JsObject => formatBashResponse(m, "%s_".format(prefix + k))
        case JsArray(list) => formatList(list, "%s_".format(prefix + k))
        case o => "%s=%s;".format(formatPosixKey(prefix + k), formatBasic(o))
      }
    }.mkString("\n")
  }

  protected def formatTextResponse(jsobject: JsValue, depth: Int = 0): String = {
    def formatBasic(jsvalue: JsValue): String = {
      jsvalue match {
        case JsNull => "null"
        case und: JsUndefined => "\"%s\"".format(und.error)
        case JsBoolean(value) => value.toString
        case JsNumber(number) => number.toString
        case JsString(s) => s
        case _ => throw new IllegalArgumentException("Unsupported JS type")
      }
    }
    def formatList(jsvalue: Seq[JsValue]): String = {
      jsvalue.map { item =>
        item match {
          case JsArray(list) => formatList(list)
          case o: JsObject => "\n" + formatTextResponse(o, depth + 1)
          case b => formatBasic(b)
        }
      }.mkString(",")
    }
    val prefix = if (depth > 0) { "\t" * depth } else { "" }
    // FIXME
    require(jsobject.isInstanceOf[JsObject], "Required a JsObject")
    jsobject.asInstanceOf[JsObject].value.map { case(k, v) =>
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
