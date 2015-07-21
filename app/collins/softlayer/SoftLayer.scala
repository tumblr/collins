package collins.softlayer

import java.net.URL

import scala.concurrent.Future
import scala.util.control.Exception.allCatch

import org.jboss.netty.handler.codec.http.QueryStringEncoder

import play.api.Logger
import play.api.Application
import play.api.Plugin
import play.api.Play.current
import play.api.libs.concurrent.Execution.Implicits.defaultContext
import play.api.libs.json.JsArray
import play.api.libs.json.JsBoolean
import play.api.libs.json.JsNumber
import play.api.libs.json.JsObject
import play.api.libs.json.JsString
import play.api.libs.json.Json
import play.api.libs.ws.WS

import collins.models.Asset
import collins.power.management.PowerCommandStatus
import collins.power.management.Failure
import collins.power.management.Success
import collins.power.management.RateLimit

trait SoftLayer {
  val SOFTLAYER_API_HOST = "api.softlayer.com:443"

  protected val logger = Logger(getClass)

  // Informational API
  def isSoftLayerAsset(asset: Asset): Boolean
  def softLayerId(asset: Asset): Option[Long]
  def softLayerUrl(asset: Asset): Option[String] =
    softLayerId(asset).map(id => "https://manage.softlayer.com/Hardware/view/%d".format(id))
  def ticketUrl(id: Long): String =
    "https://manage.softlayer.com/Support/editTicket/%d".format(id)

  // Interactive API
  def activateServer(id: Long): Future[Boolean]
  def cancelServer(id: Long, reason: String = "No longer needed"): Future[Long]
  def setNote(id: Long, note: String): Future[Boolean]

  protected def softLayerApiUrl: String =
    "https://%s:%s@%s/rest/v3".format(SoftLayerConfig.username, SoftLayerConfig.password, SOFTLAYER_API_HOST)
  protected def softLayerUrl(uri: String) = new URL(softLayerApiUrl + uri)
  protected def cancelServerPath(id: Long) =
    "/SoftLayer_Ticket/createCancelServerTicket/%d.json".format(id)
}

object SoftLayer extends SoftLayer {

  // start plugin API
  override def isSoftLayerAsset(asset: Asset): Boolean = {
    asset.tag.startsWith("sl-")
  }
  override def softLayerId(asset: Asset): Option[Long] = isSoftLayerAsset(asset) match {
    case true => try {
      Some(asset.tag.split("-", 2).last.toLong)
    } catch {
      case _: Throwable => None
    }
    case false => None
  }

  private[this] val TicketExtractor = "^.* ([0-9]+).*$".r

  override def cancelServer(id: Long, reason: String = "No longer needed"): Future[Long] = {

    val encoder = new QueryStringEncoder(cancelServerPath(id))
    encoder.addParam("attachmentId", id.toString)
    encoder.addParam("reason", "No longer needed")
    encoder.addParam("content", reason)
    val url = softLayerUrl(encoder.toString)

    WS.url(url.toString).get().map { response =>
      val json = response.json
      (json \ "error") match {
        case JsString(value) => allCatch[Long].opt {
          val TicketExtractor(number) = value
          number.toLong
        }.getOrElse(0L)
        case _ =>
          (json \ "id") match {
            case JsNumber(number) => number.longValue()
            case _                => 0L
          }
      }
    }.recover {
      case e: Throwable => 0L
    }
  }

  override def activateServer(id: Long): Future[Boolean] = {
    val url = softLayerUrl("/SoftLayer_Hardware_Server/%d/sparePool.json".format(id))
    val query = JsObject(Seq("parameters" -> JsArray(List(JsString("activate")))))
    val queryString = Json.stringify(query)

    val wsUrl = WS.url(url.toString).withHeaders("Content-Type" -> "application/json", "Content-Length" -> queryString.length.toString)

    wsUrl.post(query).map { res =>
      res.json match {
        case JsBoolean(v) => v
        case o            => false
      }
    }.recover {
      case e: Throwable => false
    }
  }

  override def setNote(id: Long, note: String): Future[Boolean] = {
    val url = softLayerUrl("/SoftLayer_Hardware_Server/%d/editObject.json".format(id))
    val query = JsObject(Seq("parameters" -> JsArray(List(JsObject(Seq("notes" -> JsString(note)))))))
    val queryString = Json.stringify(query)

    val request = WS.url(url.toString).withHeaders("Content-Type" -> "application/json", "Content-Length" -> queryString.length.toString)
    request.put(query).map { r =>
      true
    }.recover {
      case e: Throwable => false
    }
  }

  private def doPowerOperation(e: Asset, url: String, captureFn: Option[String => String] = None): Future[PowerCommandStatus] = {
    softLayerId(e).map { id =>

      val request = WS.url(softLayerUrl(url.format(id)).toString).withHeaders("Accept" -> "application/json")
      request.get().map { res =>
        res.body.toLowerCase match {
          case rl if rl.contains("at this time") => RateLimit
          case err if err.contains("error")      => Failure()
          case responseString => captureFn match {
            case None     => Success()
            case Some(fn) => Success(fn(responseString))
          }
        }
      }.recover {
        case e: Throwable => Failure("IPMI may not be enabled, internal error")
      }
    }.getOrElse(Future.successful(Failure("Asset can not be managed with SoftLayer API")))
  }

}
