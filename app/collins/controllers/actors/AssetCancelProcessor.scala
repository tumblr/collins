package collins.controllers.actors

import java.util.concurrent.TimeUnit

import scala.Left
import scala.Right
import scala.concurrent.Await
import scala.concurrent.duration.Duration
import scala.concurrent.duration.FiniteDuration

import play.api.mvc.AnyContent
import play.api.mvc.Request

import collins.controllers.Api
import collins.controllers.ResponseData
import collins.models.Asset
import collins.models.AssetLifecycle
import collins.models.MetaWrapper
import collins.models.Status
import collins.softlayer.SoftLayer
import collins.softlayer.SoftLayerConfig
import collins.util.InternalTattler
import collins.util.concurrent.BackgroundProcess

case class AssetCancelProcessor(tag: String, userTimeout: Option[FiniteDuration] = None)(implicit req: Request[AnyContent])
  extends BackgroundProcess[Either[ResponseData,Long]]
{
  val timeout = userTimeout.getOrElse(Duration(SoftLayerConfig.cancelRequestTimeoutMs, TimeUnit.MILLISECONDS))

  private val noReason : Either[ResponseData,Long] = Left(Api.getErrorMessage("No reason specified for cancellation"))

  private val softlayerDisabled : Either[ResponseData,Long] = Left(Api.getErrorMessage("SoftLayer plugin is not enabled"))

  private val nonSoftlayerAsset : Either[ResponseData,Long] = Left(Api.getErrorMessage("Asset is not a softlayer asset"))

  private val cancellationError : Either[ResponseData,Long] = Left(Api.getErrorMessage("There was an error cancelling this server"))

  def run(): Either[ResponseData,Long] = {

    val reasonOpt = req.body.asFormUrlEncoded.flatMap(_.get("reason")).flatMap(_.headOption).map(_.trim).filter(_.size > 0)

    reasonOpt.fold(noReason){ r =>
      val reason = r.trim
      Api.withAssetFromTag(tag) { asset =>
        if (SoftLayerConfig.enabled) {
          SoftLayer.softLayerId(asset).fold(nonSoftlayerAsset) { n =>

            Await.result(SoftLayer.cancelServer(n, reason), timeout) match  {
              case 0L => cancellationError
              case ticketId =>
                Asset.inTransaction {
                  MetaWrapper.createMeta(asset, Map("CANCEL_TICKET" -> ticketId.toString))
                  val lifeCycle = new AssetLifecycle(None, InternalTattler)
                  lifeCycle.updateAssetStatus(asset, Status.Cancelled, None, reason)
                }
                Await.result(SoftLayer.setNote(n, "Cancelled: %s".format(reason)), timeout)
                Right(ticketId)
            }
          }
        } else {
          softlayerDisabled
        }
      }
    }
  }
}
