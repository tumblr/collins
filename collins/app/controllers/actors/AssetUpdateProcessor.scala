package controllers
package actors

import akka.util.Duration
import play.api.mvc.{AnyContent, Request}
import util.concurrent.BackgroundProcess

case class AssetUpdateProcessor(tag: String, userTimeout: Option[Duration] = None)(implicit req: Request[AnyContent])
  extends BackgroundProcess[Either[ResponseData,Boolean]]
{
  val timeout = userTimeout.getOrElse(defaultTimeout)

  def run(): Either[ResponseData,Boolean] = {
    val assetUpdater = actions.UpdateAsset.get()
    assetUpdater.execute(tag)
  }
}
