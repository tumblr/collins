package controllers

import util._

import play.api.libs.json._
import play.api.mvc._

object ApiResponse {
  import Results._
  import OutputType.contentTypeWithCharset

  def formatJsonMessage(status: String, data: JsObject): JsObject = {
    JsObject(Seq(
      "status" -> JsString(status),
      "data" -> data
    ))
  }
  def formatJsonMessage(status: Results.Status, data: JsObject): JsObject = {
    formatJsonMessage(statusToString(status), data)
  }
  def formatJsonMessage[V](status: Results.Status, data: Seq[(String,V)])(implicit m: Manifest[V]): JsObject = {
    formatJsonMessage(statusToString(status), data)
  }
  def formatJsonMessage[V](status: String, data: Seq[(String,V)])(implicit m: Manifest[V]): JsObject = {
    val jso = m match {
      case s if s == Manifest.classType(classOf[String]) =>
        JsObject(data.map(kv => kv._1 -> JsString(kv._2.asInstanceOf[String])))
      case j if j == Manifest.classType(classOf[JsValue]) =>
        JsObject(data.asInstanceOf[Seq[(String,JsValue)]])
      case _ =>
        throw new RuntimeException("Unhandled conversion for Map with values of type " + m.toString)
    }
    formatJsonMessage(status, jso)
  }

  def isJsonErrorMessage(js: JsValue): Boolean = {
    (js \ "status").asOpt[String].map(e => e.contains("error")).getOrElse(false)
  }
  def getJsonErrorMessage(js: JsValue, default: String) = {
    val isError = (js \ "status").asOpt[String].map(e => e.contains("error")).getOrElse(false)
    if (isError) {
      (js \ "data" \ "message").asOpt[String].getOrElse(default)
    } else {
      default
    }
  }

  def formatJsonError(msg: String, ex: Option[Throwable]): JsObject = {
    val seq = Seq("message" -> JsString(msg))
    val exSeq = ex.map { e =>
      Seq("details" -> JsObject(formatException(e).map(kv => kv._1 -> JsString(kv._2))))
    }.getOrElse(Seq())
    val dataSeq = seq ++ exSeq
    formatJsonMessage("error", JsObject(dataSeq))
  }

  def bashError(msg: String, status: Results.Status = Results.BadRequest, ex: Option[Throwable]) = {
    val exSeq = ex.map { e => formatException(e) }.getOrElse(Seq())
    val exMsg = exSeq.map { case(k,v) =>
      """DATA_EXCEPTION_%s='%s';""".format(k.toUpperCase, v)
    }.mkString("\n")
    val output =
"""STATUS="error";
DATA_MESSAGE='%s';
%s
""".format(msg, exMsg)
    status(output).as(contentTypeWithCharset(BashOutput()))
  }

  def statusToString(status: Results.Status): String = {
    status.header.status match {
      case 200 => "successful -> ok"
      case 201 => "successful -> created"
      case 202 => "successful -> accepted"
      case ok if ok >= 200 && ok < 300 => "successful -> other"
      case 400 => "client error -> bad request"
      case 401 => "client error -> unauthorized"
      case 403 => "client error -> forbidden"
      case 404 => "client error -> not found"
      case 405 => "client error -> method not allowed"
      case 406 => "client error -> not acceptable"
      case 409 => "client error -> conflict"
      case userErr if userErr >= 400 && userErr < 500 => "client error -> unknown"
      case 500 => "server error -> internal server error"
      case 501 => "server error -> not implemented"
      case srvErr if srvErr >= 500 => "server error -> unknown"
      case n => "unknown -> %d".format(n)
    }
  }

  def jsonError(msg: String, status: Results.Status = Results.BadRequest, ex: Option[Throwable]) = {
    val output: JsValue = formatJsonError(msg, ex)
    status(output).as(contentTypeWithCharset(JsonOutput()))
  }

  def textError(msg: String, status: Results.Status = Results.BadRequest, ex: Option[Throwable]) = {
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
    status(output).as(contentTypeWithCharset(TextOutput()))
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

  protected def formatResponseData(response: ResponseData)(implicit req: Request[AnyContent]) = {
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


