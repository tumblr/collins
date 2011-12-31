package controllers

import util._

import play.api.libs.json._
import play.api.mvc._

object ApiResponse {
  import Results._
  import OutputType.contentTypeWithCharset

  def bashError(msg: String, status: Results.Status = Results.BadRequest, ex: Option[Throwable]) = {
    val exMap = ex.map { e => formatException(e) }.getOrElse(Map.empty)
    val exMsg = exMap.map { case(k,v) =>
      """DATA_EXCEPTION_%s='%s';""".format(k.toUpperCase, v)
    }.mkString("\n")
    val output =
"""STATUS="error";
DATA_MESSAGE="%s";
%s
""".format(msg, exMsg)
    status(output).as(contentTypeWithCharset(BashOutput()))
  }

  def jsonError(msg: String, status: Results.Status = Results.BadRequest, ex: Option[Throwable]) = {
    val map = Map("message" -> JsString(msg))
    val exMap = ex.map { e =>
      val details = formatException(e).map { case(k,v) =>
        k -> JsString(v)
      }
      Map("details" -> JsObject(details))
    }.getOrElse(Map.empty)
    val dataMap = map ++ exMap
    val output: JsValue = JsObject(Map(
      "status" -> JsString("error"),
      "data" -> JsObject(dataMap)
    ))
    status(output).as(contentTypeWithCharset(JsonOutput()))
  }

  def textError(msg: String, status: Results.Status = Results.BadRequest, ex: Option[Throwable]) = {
    val exMap = ex.map { e => formatException(e) }.getOrElse(Map.empty)
    val exMsg = exMap.map { case(k,v) => """
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

  private def formatException(ex: Throwable) = {
    Map("classOf" -> ex.getClass.toString,
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


