package collins.events

import scala.concurrent.Await
import scala.concurrent.duration._
import scala.concurrent.duration.Duration

import play.api.Logger
import play.api.Play.current
import play.api.libs.concurrent.Akka

import collins.callbacks.Callback

import akka.actor.ActorRef
import akka.actor.Props
import akka.routing.FromConfig

object Events {
  private[this] val logger = Logger(getClass)
  private[this] val registerDuration = 100 millis

  def setupEvents() {

    if (EventsConfig.enabled) {
      logger.info("Installing callbacks for events")
      val processor = Akka.system.actorOf(Props[EventProcessor].withRouter(FromConfig()), name = "event_processor")

      val callback = EventsCallbackHandler(processor)
      Callback.on("asset_update", callback)
      Callback.on("asset_create", callback)
      Callback.on("asset_delete", callback)
      Callback.on("asset_purge", callback)
      Callback.on("ipAddresses_create", callback)
      Callback.on("ipAddresses_update", callback)
      Callback.on("ipAddresses_delete", callback)
    }
  }

  def registerCallback(ws: ActorRef) = {
    logger.info("Registering a callback with the processor")
    val processor = Akka.system.actorSelection("/user/event_processor")
    Props(new EventWriter(ws, Await.result(processor.resolveOne(registerDuration), registerDuration)))
  }
}