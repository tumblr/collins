package util

import com.tumblr.play.{SoftLayerClientApi, SoftLayerClientPlugin}
import com.twitter.util.Future
import play.api.{Application, Mode}
import java.io.File

object SoftLayerClient extends SoftLayerClientApi {
  private[this] def getSLPlugin =
    new SoftLayerClientPlugin(new Application(new File("."), this.getClass.getClassLoader, None, Mode.Dev))
  private[this] val slPlugin = play.api.Play.maybeApplication.map { app =>
    app.plugin[SoftLayerClientPlugin].getOrElse(getSLPlugin)
  }.getOrElse(getSLPlugin)

  def pluginEnabled: Option[Boolean] = {
    play.api.Play.maybeApplication.map { app =>
      app.plugin[SoftLayerClientPlugin].map { _ => true }.getOrElse(false)
    }
  }

  def ticketLink(id: String): Option[String] = {
    try {
      Some("https://manage.softlayer.com/Support/editTicket/%d".format(id.toLong))
    } catch {
      case e => None
    }
  }
  override def cancelServer(id: Long, reason: String = "No longer needed"): Future[Long] =
    slPlugin.cancelServer(id, reason)
  override def setNote(id: Long, note: String): Future[Boolean] =
    slPlugin.setNote(id, note)
}
