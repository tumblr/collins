package controllers
package actions
package asset

import util.{AppConfig, Config, Provisioner, SoftLayer}
import util.concurrent.RateLimiter
import util.security.SecuritySpecification
import validators.StringUtil

import play.api.data.Form
import play.api.data.Forms._
import play.api.mvc._

case class ProvisionAction(
  assetTag: String,
  spec: SecuritySpecification,
  handler: SecureController
) extends SecureAction(spec, handler) with Provisions {

  private val rateLimiter = RateLimiter(Config.getString("provisioner", "rate", "1/0 seconds"))

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

  override def execute(rd: RequestDataHolder) = AsyncResult {
    rd match {
      case adh@ActionDataHolder(_, _, activate, _) =>
        rateLimiter.tick(user.id.toString) // we will reset on error
        try {
          if (activate)
            activateAsset(adh)
          else
            provisionAsset(adh)
        } catch {
          case e =>
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
