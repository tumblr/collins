package collins.controllers.actions.asset

import scala.concurrent.Future

import play.api.libs.concurrent.Execution.Implicits.defaultContext
import play.api.mvc.Result

import collins.controllers.ResponseData
import collins.controllers.SecureController
import collins.controllers.actions.AssetAction
import collins.controllers.actions.RequestDataHolder
import collins.controllers.actions.SecureAction
import collins.models.Asset
import collins.util.RemoteCollinsHost
import collins.util.security.SecuritySpecification

import views.html

case class GetAction(
  assetTag: String,
  location: Option[String],
  spec: SecuritySpecification,
  handler: SecureController
) extends SecureAction(spec, handler) with AssetAction {

  case class AssetDataHolder(asset: Asset) extends RequestDataHolder
  case class RedirectDataHolder(host: RemoteCollinsHost) extends RequestDataHolder

  override def validate(): Either[RequestDataHolder,RequestDataHolder] = location match {
    case Some(locationTag) =>
      assetFromTag(locationTag) match {
        case None => Left(RequestDataHolder.error404("Unknown location %s".format(locationTag)))
        case Some(locationAsset) => locationAsset.getMetaAttribute("LOCATION").map(_.getValue) match {
          case None => Left(RequestDataHolder.error500("No LOCATION attribute for remote instance %s".format(locationTag)))
          case Some(host) => try {
            val collinsHost = RemoteCollinsHost(host)
            Right(RedirectDataHolder(collinsHost))
          } catch {
            case e: Throwable =>
              Left(RequestDataHolder.error500("Invalid LOCATION url for asset %s: %s".format(
                locationTag, e.getMessage
              )))
          }
        }
      }
    case None =>
      assetFromTag(assetTag) match {
        case None => Left(assetNotFound(assetTag))
        case Some(asset) => Right(AssetDataHolder(asset))
      }
  }

  override def execute(rd: RequestDataHolder) = Future {
    rd match {
      case AssetDataHolder(asset) => handleSuccess(asset)
      case RedirectDataHolder(host) => isHtml match {
        case true =>
          Status.MovedPermanently(host.host + collins.app.routes.CookieApi.getAsset(assetTag))
        case false =>
          Status.MovedPermanently(host.host + collins.app.routes.Api.getAsset(assetTag, None))
      }
    }
  }

  override def handleWebError(rd: RequestDataHolder) = {
    val msg = rd.error.getOrElse(AssetMessages.notFound(assetTag))
    Some(Redirect(collins.app.routes.Resources.index).flashing("message" -> msg))
  }

  protected def handleSuccess(asset: Asset): Result = {
    val display = asset.getAllAttributes.exposeCredentials(user.canSeePasswords)
    isHtml match {
      case true =>
        Status.Ok(html.asset.show(display, user)(flash, request))
      case false =>
        ResponseData(Status.Ok, display.toJsValue)
    }
  }

}

