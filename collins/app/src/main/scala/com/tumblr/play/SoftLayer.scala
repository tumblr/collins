package com.tumblr.play

import play.api.{Application, Configuration, Logger, PlayException, Plugin}
import play.api.libs.json._

import com.twitter.finagle.Service
import com.twitter.finagle.builder.ClientBuilder
import com.twitter.finagle.http.{Http, RequestBuilder, Response}
import com.twitter.util.Future
import java.net.URL
import org.jboss.netty.buffer.ChannelBuffers
import org.jboss.netty.handler.codec.http.{HttpRequest, HttpResponse, QueryStringEncoder}
import org.jboss.netty.util.CharsetUtil.UTF_8
import scala.util.control.Exception.allCatch

trait SoftLayerInterface extends PowerManagement {
  val SOFTLAYER_API_HOST = "api.softlayer.com:443"

  protected val logger = Logger(getClass)
  protected def username: String
  protected def password: String

  // Informational API
  def isSoftLayerAsset(asset: AssetWithTag): Boolean
  def softLayerId(asset: AssetWithTag): Option[Long]
  def softLayerUrl(asset: AssetWithTag): Option[String] =
    softLayerId(asset).map(id => "https://manage.softlayer.com/Hardware/view/%d".format(id))
  def ticketUrl(id: Long): String =
    "https://manage.softlayer.com/Support/editTicket/%d".format(id)

  // Interactive API
  def activateServer(id: Long): Future[Boolean]
  def cancelServer(id: Long, reason: String = "No longer needed"): Future[Long]
  def setNote(id: Long, note: String): Future[Boolean]

  protected def softLayerApiUrl: String =
    "https://%s:%s@%s/rest/v3".format(username, password, SOFTLAYER_API_HOST)
  protected def softLayerUrl(uri: String) = new URL(softLayerApiUrl + uri)
  protected def cancelServerPath(id: Long) =
    "/SoftLayer_Ticket/createCancelServerTicket/%d.json".format(id)
}

class SoftLayerPlugin(app: Application) extends Plugin with SoftLayerInterface {
  protected[this] val configuration: Option[Configuration] = app.configuration.getConfig("softlayer")
  protected[this] val _username: Option[String] = configuration.flatMap(_.getString("username"))
  protected[this] val _password: Option[String] = configuration.flatMap(_.getString("password"))
  protected[this] val cachePlugin = new CachePlugin(app, None, 86400)
  protected[this] def InvalidConfig(s: Option[String] = None): Exception = PlayException(
    "Invalid Configuration",
    s.getOrElse("softlayer.enabled is true but username or password not specified"),
    None
  )

  type ClientSpec = ClientBuilder.Complete[HttpRequest, HttpResponse]
  protected[this] val clientSpec: ClientSpec = ClientBuilder()
    .tlsWithoutValidation()
    .codec(Http())
    .hosts(SOFTLAYER_API_HOST)
    .hostConnectionLimit(1)

  override def enabled: Boolean = {
    configuration.flatMap { cfg =>
      cfg.getBoolean("enabled")
    }.getOrElse(false)
  }

  override def onStart() {
    if (!_username.isDefined || !_password.isDefined) {
      throw InvalidConfig()
    }
  }
  override def onStop() {
    cachePlugin.clear()
    cachePlugin.onStop()
  }

  override def username = _username.get
  override def password = _password.get

  // start plugin API
  override def isSoftLayerAsset(asset: AssetWithTag): Boolean = {
    asset.tag.startsWith("sl-")
  }
  override def softLayerId(asset: AssetWithTag): Option[Long] = isSoftLayerAsset(asset) match {
    case true => try {
      Some(asset.tag.split("-", 2).last.toLong)
    } catch {
      case _ => None
    }
    case false => None
  }

  private[this] val TicketExtractor = "^.* ([0-9]+).*$".r
  override def cancelServer(id: Long, reason: String = "No longer needed"): Future[Long] = {
    val encoder = new QueryStringEncoder(cancelServerPath(id))
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

  /*
  override def powerCycle(e: AssetWithTag): PowerStatus = {
    doPowerOperation(e, "/SoftLayer_Hardware_Server/%d/powerCycle.json")
  }
  */
  override def powerSoft(e: AssetWithTag): PowerStatus = {
    // This does not actually exist at SL
    doPowerOperation(e, "/SoftLayer_Hardware_Server/%d/powerSoft.json")
  }
  override def powerOff(e: AssetWithTag): PowerStatus = {
    doPowerOperation(e, "/SoftLayer_Hardware_Server/%d/powerOff.json")
  }
  override def powerOn(e: AssetWithTag): PowerStatus = {
    doPowerOperation(e, "/SoftLayer_Hardware_Server/%d/powerOn.json")
  }
  override def powerState(e: AssetWithTag): PowerStatus = {
    doPowerOperation(e, "/SoftLayer_Hardware_Server/%d/getServerPowerState.json", Some({ s =>
      s.replace("\"", "")
    }))
  }
  override def rebootHard(e: AssetWithTag): PowerStatus = {
    doPowerOperation(e, "/SoftLayer_Hardware_Server/%d/rebootHard.json")
  }
  override def rebootSoft(e: AssetWithTag): PowerStatus = {
    doPowerOperation(e, "/SoftLayer_Hardware_Server/%d/rebootSoft.json")
  }

  override def activateServer(id: Long): Future[Boolean] = {
    val url = softLayerUrl("/SoftLayer_Hardware_Server/%d/sparePool.json".format(id))
    val query = JsObject(Seq("parameters" -> JsArray(List(JsString("activate")))))
    val queryString = Json.stringify(query)
    val value = ChannelBuffers.copiedBuffer(queryString, UTF_8)
    val request = RequestBuilder()
      .url(url)
      .setHeader("Content-Type", "application/json")
      .setHeader("Content-Length", queryString.length.toString)
      .buildPost(value)
    makeRequest(request) map { r =>
      val response = Response(r)
      Json.parse(response.contentString) match {
        case JsBoolean(v) => v
        case o => false
      }
    } handle {
      case e => false
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

  private def doPowerOperation(e: AssetWithTag, url: String, captureFn: Option[String => String] = None): PowerStatus = {
    softLayerId(e).map { id =>
      val request = RequestBuilder()
        .url(softLayerUrl(url.format(id)))
        .setHeader("Accept", "application/json")
        .buildGet();
      makeRequest(request).map { r =>
        Response(r).contentString.toLowerCase match {
          case rl if rl.contains("at this time") => RateLimit
          case err if err.contains("error") => Failure()
          case responseString => captureFn match {
            case None => Success()
            case Some(fn) => Success(fn(responseString))
          }
        }
      } handle { 
        case e => Failure("IPMI may not be enabled, internal error")
      }
    }.getOrElse(Future(Failure("Asset can not be managed with SoftLayer API")))
  }

}
