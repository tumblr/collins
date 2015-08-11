package collins.events

import play.api.Logger
import play.api.Play.current
import play.api.libs.concurrent.Akka

import collins.callbacks.Callback

import akka.actor.Props
import akka.routing.FromConfig

object Events {
  private[this] val logger = Logger(getClass)

  def setupEvents() {
    val writer = Akka.system.actorOf(Props[EventWriter].withRouter(FromConfig()), name = "event_writer")

    val callback = EventsCallbackHandler(writer)
    Callback.on("asset_update", callback)
    Callback.on("asset_create", callback)
    Callback.on("asset_delete", callback)
    Callback.on("asset_purge", callback)
    Callback.on("ipAddresses_create", callback)
    Callback.on("ipAddresses_update", callback)
    Callback.on("ipAddresses_delete", callback)
  }
}