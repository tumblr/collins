package collins.controllers.actions.asset

import scala.concurrent.Future

import play.api.mvc.SimpleResult
import play.api.mvc.AsyncResult

import collins.controllers.Permissions
import collins.controllers.SecureController
import collins.controllers.actions.RequestDataHolder
import collins.controllers.actions.SecureAction
import collins.provisioning.ProvisionerConfig
import collins.util.concurrent.RateLimiter
import collins.util.config.AppConfig
import collins.util.plugins.Provisioner
import collins.util.security.SecuritySpecification

case class ProvisionAction(
  assetTag: String,
  spec: SecuritySpecification,
  handler: SecureController
) extends SecureAction(spec, handler) with Provisions {

  private val rateLimiter = RateLimiter(ProvisionerConfig.rate)

  override def validate(): Validation = withValidAsset(assetTag) { asset =>
    if (isRateLimited) {
      return Left(
        RequestDataHolder.error429("Request rate limited by configuration")
      )
    }
    if (AppConfig.ignoreAsset(asset))
      return Left(
        RequestDataHolder.error403("Asset has been configured to ignore dangerous commands")
      )
    val plugin = Provisioner.plugin
    if (!plugin.isDefined)
      return Left(
        RequestDataHolder.error501("Provisioner plugin not enabled")
      )
    provisionForm.bindFromRequest()(request).fold(
      errorForm => fieldError(errorForm),
      okForm => validate(plugin.get, asset, okForm)
    )
  }

  override def execute(rd: RequestDataHolder) = {
    rd match {
      case adh@ActionDataHolder(_, _, activate, _) =>
        rateLimiter.tick(user.id.toString) // we will reset on error
        try {
          if (activate)
            activateAsset(adh)
          else
            provisionAsset(adh)
        } catch {
          case e: Throwable =>
            onFailure()
            throw e
        }
    }
  }

  override protected def onSuccess() {
    rateLimiter.tick(user.id.toString)
  }
  override protected def onFailure() {
    rateLimiter.untick(user.id.toString)
  }

  protected def isRateLimited: Boolean = {
    if (Permissions.please(user, Permissions.Feature.NoRateLimit))
      false
    else
      rateLimiter.isLimited(user.id.toString)
  }

}
