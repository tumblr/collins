package com.tumblr.play

import play.api.{Application, PlayException, Plugin}
import play.api.libs.json._

import com.twitter.finagle.Service
import com.twitter.finagle.builder.ClientBuilder
import com.twitter.finagle.http.{Http, ProxyCredentials, RequestBuilder, Response}
import com.twitter.util.Future
import org.jboss.netty.buffer.ChannelBuffers
import org.jboss.netty.handler.codec.http.{HttpRequest, HttpResponse, QueryStringEncoder}
import org.jboss.netty.util.CharsetUtil.UTF_8
import scala.util.control.Exception.allCatch
import java.net.URL

trait SoftLayerClientApi {
  // Return value is ticket id
  def cancelServer(id: Long, reason: String = "No longer needed"): Future[Long]
  def setNote(id: Long, note: String): Future[Boolean]
}

class SoftLayerClientPlugin(app: Application) extends Plugin with SoftLayerClientApi {

  type ClientSpec = ClientBuilder.Complete[HttpRequest, HttpResponse]

  val SOFTLAYER_API_URL_TEMPLATE = "https://%s:%s@api.softlayer.com/rest/v3"
  lazy val SOFTLAYER_API_URL = {
    val p = getProxyCredentials()
    SOFTLAYER_API_URL_TEMPLATE.format(p.username, p.password)
  }
  protected[this] val clientSpec: ClientSpec = ClientBuilder()
    .tlsWithoutValidation()
    .codec(Http())
    .hosts("api.softlayer.com:443")
    .hostConnectionLimit(1)

  private[this] val pluginDisabled = app.configuration.getString("softLayerClientPlugin.class").filter(_ == "com.tumblr.play.SoftLayerClientPlugin").headOption

  override def enabled = pluginDisabled.isDefined == true
  override def onStart() {
    getProxyCredentials()
  }
  override def onStop() {}

  // start client api
  private[this] val TicketExtractor = "^.* ([0-9]+).*$".r
  override def cancelServer(id: Long, reason: String = "No longer needed"): Future[Long] = {
    val encoder = new QueryStringEncoder(cancelServerUrl(id))
    encoder.addParam("attachmentId", id.toString)
    encoder.addParam("reason", "No longer needed")
    encoder.addParam("content", reason)
    val url = softLayerUrl(encoder.toString())
    val request = RequestBuilder()
                    .url(url)
                    .buildGet();

    makeRequest(request) map { r =>
      val response = Response(r)
      val json = Json.parse(response.contentString)
      (json \ "error" ) match {
        case JsString(value) => allCatch[Long].opt {
          val TicketExtractor(number) = value
          number.toLong
        }.getOrElse(0L)
        case _ =>
          (json \ "id") match {
            case JsNumber(number) => number.longValue()
            case _ => 0L
          }
      }
    } handle {
      case e => 0L
    }
  }

  override def setNote(id: Long, note: String): Future[Boolean] = {
    val url = softLayerUrl("/SoftLayer_Hardware_Server/%d/editObject.json".format(id))
    val query = JsObject(Seq("parameters" -> JsArray(List(JsObject(Seq("notes" -> JsString(note)))))))
    val queryString = Json.stringify(query)
    val value = ChannelBuffers.copiedBuffer(queryString, UTF_8)
    val request = RequestBuilder()
      .url(url)
      .setHeader("Content-Type", "application/json")
      .setHeader("Content-Length", queryString.length.toString)
      .buildPut(value)
    makeRequest(request) map { r =>
      true
    } handle {
      case e => false
    }
  }

  protected def makeRequest(request: HttpRequest): Future[HttpResponse] = {
    val client: Service[HttpRequest,HttpResponse] = clientSpec.build()
    client(request) ensure {
      client.release()
    }
  }

  protected def cancelServerUrl(id: Long) =
    "/SoftLayer_Ticket/createCancelServerTicket/%d.json".format(id)
  protected def softLayerUrl(uri: String) = new URL(SOFTLAYER_API_URL + uri)
  protected def getProxyCredentials(): ProxyCredentials = {
    val username = app.configuration.getString("softLayerClient.username").getOrElse {
      throw PlayException("No username specified", "softLayerClient.username must be specified", None)
    }
    val password = app.configuration.getString("softLayerClient.password").getOrElse {
      throw PlayException("No password specified", "softLayerClient.password must be specified", None)
    }
    ProxyCredentials(username, password)
  }

}
