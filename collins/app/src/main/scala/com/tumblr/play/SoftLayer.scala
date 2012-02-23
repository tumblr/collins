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

trait SoftLayerInterface {
  val SOFTLAYER_API_HOST = "api.softlayer.com:443"
  protected val logger = Logger(getClass)
  protected def username: String
  protected def password: String

  type AssetWithTag = {
    def tag: String
  }

  // Informational API
  def isSoftLayerAsset(asset: AssetWithTag): Boolean
  def softLayerId(asset: AssetWithTag): Option[Long]
  def softLayerUrl(asset: AssetWithTag): Option[String] =
    softLayerId(asset).map(id => "https://manage.softlayer.com/Hardware/view/%d".format(id))
  def ticketUrl(id: Long): String =
    "https://manage.softlayer.com/Support/editTicket/%d".format(id)

  // Interactive API
  def cancelServer(id: Long, reason: String = "No longer needed"): Future[Long]
  def getTicketSubjects(): Seq[Tuple2[Long,String]]
  def rebootServer(id: Long, rebootType: RebootType): Future[Boolean]
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

  override def rebootServer(id: Long, rebootType: RebootType = RebootSoft): Future[Boolean] = {
    val url = rebootType match {
      case RebootSoft => softLayerUrl("/SoftLayer_Hardware_Server/%d/rebootSoft.json".format(id))
      case RebootHard => softLayerUrl("/SoftLayer_Hardware_Server/%d/rebootHard.json".format(id))
    }
    val request = RequestBuilder()
      .url(url)
      .setHeader("Content-Type", "application/json")
      .buildGet();
    makeRequest(request) map { r =>
      true
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

  def createTicket(): Unit = {
    val subjects = getTicketSubjects()
    subjects.foreach { s =>
      println(s)
    }
  }

  def getTicketSubjects(): Seq[Tuple2[Long,String]] = {
    cachePlugin.getOrElseUpdate("ticketSubjects", {
      val url = softLayerUrl("/SoftLayer_Ticket_Subject/getAllObjects")
      val request = RequestBuilder()
        .url(url)
        .setHeader("Content-Type", "application/json")
        .buildGet()
      val future = makeRequest(request) map { r =>
        val content = Response(r).contentString
        val json = Json.parse(content)
        json match {
          case JsArray(values) =>
            try {
              values.foldLeft(Seq[Tuple2[Long,String]]()) { case (total, current) =>
                val id = (current \ "id").as[Long]
                val name = (current \ "name").as[String]
                val tuple = (id, name)
                Seq(tuple) ++ total
              }.sortBy(_._2)
            } catch {
              case e =>
                logger.warn("Unable to get ticket subjects, invalid format")
                Seq()
            }
          case n =>
            logger.warn("Unexpected response from API")
            Seq()
        }
      } handle {
        case e =>
          logger.warn("Error getting ticket subjects", e)
          Seq()
      }
      future()
    })
  }

  protected def makeRequest(request: HttpRequest): Future[HttpResponse] = {
    val client: Service[HttpRequest,HttpResponse] = clientSpec.build()
    client(request) ensure {
      client.release()
    }
  }

}
