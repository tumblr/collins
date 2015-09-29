package collins.firehose

import play.api.Logger
import play.api.Play.current
import play.api.libs.concurrent.Akka
import play.api.libs.iteratee.Concurrent
import play.api.libs.json.JsValue

import com.hazelcast.core.Message
import com.hazelcast.core.MessageListener

import collins.callbacks.Callback
import collins.hazelcast.HazelcastHelper

import akka.actor.Props
import akka.routing.FromConfig

object Firehose {
  private[this] val logger = Logger(getClass)

  /** Central hub for distributing firehose events  */
  val (out, channel) = Concurrent.broadcast[JsValue]

  def setupFirehose() {

    if (FirehoseConfig.enabled) {
      logger.info("Installing callbacks for firehose")
      val processor = Akka.system.actorOf(Props[FirehoseProcessor].withRouter(FromConfig()), name = "firehose_processor")

      val callback = FirehoseCallbackHandler(processor)
      Callback.on("asset_update", callback)
      Callback.on("asset_create", callback)
      Callback.on("asset_delete", callback)
      Callback.on("asset_purge", callback)
      Callback.on("ipAddresses_create", callback)
      Callback.on("ipAddresses_update", callback)
      Callback.on("ipAddresses_delete", callback)
    }

    // instantiate a listener for topic events
    val topic = HazelcastHelper.getTopic()
    val ml = new MessageListener[JsValue]() {
      def onMessage(message: Message[JsValue]) {
        val mo = message.getMessageObject
        channel.push(mo)
      }
    }

    topic.map(_.addMessageListener(ml))
  }
}