package collins.softlayer

import java.net.URL

import scala.concurrent.Future

import play.api.Logger

import collins.models.Asset
import collins.power.management.PowerManagement

trait SoftLayer extends PowerManagement {
  val SOFTLAYER_API_HOST = "api.softlayer.com:443"

  protected val logger = Logger(getClass)
  protected def username: String
  protected def password: String

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
    "https://%s:%s@%s/rest/v3".format(username, password, SOFTLAYER_API_HOST)
  protected def softLayerUrl(uri: String) = new URL(softLayerApiUrl + uri)
  protected def cancelServerPath(id: Long) =
    "/SoftLayer_Ticket/createCancelServerTicket/%d.json".format(id)
}
