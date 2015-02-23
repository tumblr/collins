package collins.softlayer

import models.Asset
import play.api.libs.ws.WS

import play.api.{Application, Plugin}
import play.api.libs.json._
import scala.concurrent.Future
import org.jboss.netty.handler.codec.http.QueryStringEncoder
import scala.util.control.Exception.allCatch
import play.api.libs.concurrent.Execution.Implicits._

class SoftLayerPlugin(app: Application) extends Plugin with SoftLayer {

  override def enabled: Boolean = {
    SoftLayerConfig.pluginInitialize(app.configuration)
    SoftLayerConfig.enabled
  }

  override def onStart() {
    SoftLayerConfig.validateConfig()
  }
  override def onStop() {
  }

  override def username = SoftLayerConfig.username
  override def password = SoftLayerConfig.password

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


    WS.url(url.toString).get().map{ response =>
      val json = response.json
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
    }.recover {
      case e : Throwable => 0L
    }
  }

  /*
  override def powerCycle(e: Asset): PowerStatus = {
    doPowerOperation(e, "/SoftLayer_Hardware_Server/%d/powerCycle.json")
  }
  */
  override def powerSoft(e: Asset): PowerStatus = {
    // This does not actually exist at SL
    doPowerOperation(e, "/SoftLayer_Hardware_Server/%d/powerSoft.json")
  }
  override def powerOff(e: Asset): PowerStatus = {
    doPowerOperation(e, "/SoftLayer_Hardware_Server/%d/powerOff.json")
  }
  override def powerOn(e: Asset): PowerStatus = {
    doPowerOperation(e, "/SoftLayer_Hardware_Server/%d/powerOn.json")
  }
  override def powerState(e: Asset): PowerStatus = {
    doPowerOperation(e, "/SoftLayer_Hardware_Server/%d/getServerPowerState.json", Some({ s =>
      s.replace("\"", "")
    }))
  }
  override def rebootHard(e: Asset): PowerStatus = {
    doPowerOperation(e, "/SoftLayer_Hardware_Server/%d/rebootHard.json")
  }
  override def rebootSoft(e: Asset): PowerStatus = {
    doPowerOperation(e, "/SoftLayer_Hardware_Server/%d/rebootSoft.json")
  }
  override def verify(e: Asset): PowerStatus = {
    Future.successful(Failure("verify not implemented for softlayer"))
  }
  override def identify(e: Asset): PowerStatus = {
    Future.successful(Failure("identify not implemented for softlayer"))
  }

  override def activateServer(id: Long): Future[Boolean] = {
    val url = softLayerUrl("/SoftLayer_Hardware_Server/%d/sparePool.json".format(id))
    val query = JsObject(Seq("parameters" -> JsArray(List(JsString("activate")))))
    val queryString = Json.stringify(query)

    val wsUrl = WS.url(url.toString).withHeaders("Content-Type"->"application/json", "Content-Length"-> queryString.length.toString)

    wsUrl.post(query).map{ res =>
      res.json match {
        case JsBoolean(v) => v
        case o => false
      }
    }.recover {
      case e : Throwable => false
    }
  }

  override def setNote(id: Long, note: String): Future[Boolean] = {
    val url = softLayerUrl("/SoftLayer_Hardware_Server/%d/editObject.json".format(id))
    val query = JsObject(Seq("parameters" -> JsArray(List(JsObject(Seq("notes" -> JsString(note)))))))
    val queryString = Json.stringify(query)

    val request = WS.url(url.toString).withHeaders("Content-Type"->"application/json", "Content-Length"-> queryString.length.toString)
    request.put(query).map { r =>
      true
    }.recover {
      case e : Throwable => false
    }
  }

  private def doPowerOperation(e: Asset, url: String, captureFn: Option[String => String] = None): PowerStatus = {
    softLayerId(e).map { id =>

      val request = WS.url(softLayerUrl(url.format(id)).toString).withHeaders("Accept"->"application/json")
      request.get().map { res =>
        res.body.toLowerCase match {
          case rl if rl.contains("at this time") => RateLimit
          case err if err.contains("error") => Failure()
          case responseString => captureFn match {
            case None => Success()
            case Some(fn) => Success(fn(responseString))
          }
        }
      }.recover {
        case e : Throwable => Failure("IPMI may not be enabled, internal error")
      }
    }.getOrElse(Future.successful(Failure("Asset can not be managed with SoftLayer API")))
  }

}
