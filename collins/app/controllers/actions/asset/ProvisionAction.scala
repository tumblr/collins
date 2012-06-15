package controllers
package actions
package asset

import util.{AppConfig, Provisioner, SecuritySpecification, SoftLayer}
import validators.StringUtil

import play.api.data.Form
import play.api.data.Forms._
import play.api.mvc._

case class ProvisionAction(
  assetTag: String,
  spec: SecuritySpecification,
  handler: SecureController
) extends SecureAction(spec, handler) with Provisions {

  override def validate(): Validation = withValidAsset(assetTag) { asset =>
    if (AppConfig.ignoreAsset(asset))
      return Left(
        RequestDataHolder.error400("Asset has been configured to ignore dangerous commands")
      )
    val plugin = Provisioner.plugin
    if (!plugin.isDefined)
      return Left(
        RequestDataHolder.error500("Provisioner plugin not enabled")
      )
    provisionForm.bindFromRequest()(request).fold(
      errorForm => fieldError(errorForm),
      okForm => validate(plugin.get, asset, okForm)
    )
  }

  override def execute(rd: RequestDataHolder) = AsyncResult {
    rd match {
      case adh@ActionDataHolder(_, _, activate, _) =>
        if (activate)
          activateAsset(adh)
        else
          provisionAsset(adh)
    }
  }

}
