package collins.firehose

import play.api.Logger
import play.api.libs.json.JsObject
import play.api.libs.json.JsString

import collins.hazelcast.HazelcastHelper
import collins.models.asset.AllAttributes

import akka.actor.Actor

case class Event(name: String, tag: String, category: Category, asset: Option[AllAttributes]) {
  def this(name: String, category: Category, asset: AllAttributes) {
    this(name, asset.asset.tag, category, Some(asset))
  }
}

class FirehoseProcessor extends Actor {
  private[this] val logger = Logger(getClass)

  def receive = {
    case Event(name, tag, category, asset) =>
      logger.trace("Received a message of category %s for asset with tag %s on name %s".format(category, tag, name))

      val event = JsObject(Seq(
        "name" -> JsString(name),
        "tag" -> JsString(tag),
        "category" -> JsString(category.toString),
        "data" -> asset.map(_.toJsValue()).getOrElse(JsObject(Seq()))))

      val topic = HazelcastHelper.getTopic()
      topic.map(_.publish(event))
  }
}