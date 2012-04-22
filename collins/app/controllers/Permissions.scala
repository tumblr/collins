package controllers

import models.User
import util._

object Permissions {
  val LoggedIn = SecuritySpec.fromConfig("controllers.Api", SecuritySpec(true))
  val AdminSpec = SecuritySpec(true, AppConfig.adminGroup)

  def please(user: User, spec: SecuritySpecification): Boolean = {
    AuthenticationProvider.userIsAuthorized(user, spec)
  }

  object Admin {
    val Spec = SecuritySpec.fromConfig("controllers.Admin", AdminSpec)
    val Stats = SecuritySpec.fromConfig("controllers.Admin.stats", Spec)
    val Logs = SecuritySpec.fromConfig("controllers.Admin.logs", Spec)
    val ClearCache = SecuritySpec.fromConfig("controllers.Admin.clearCache", Stats)
  }

  object AssetApi {
    val Spec = SecuritySpec.fromConfig("controllers.AssetApi", LoggedIn)
    val CreateAsset = SecuritySpec.fromConfig("controllers.AssetApi.createAsset", AdminSpec)
    val DeleteAsset = SecuritySpec.fromConfig("controllers.AssetApi.deleteAsset", AdminSpec)
    val DeleteAssetAttribute =
      SecuritySpec.fromConfig("controllers.AssetApi.deleteAssetAttribute", AdminSpec)
    val GetAsset = SecuritySpec.fromConfig("controllers.AssetApi.getAsset", Spec)
    val GetAssets = SecuritySpec.fromConfig("controllers.AssetApi.getAssets", Spec)
    val UpdateAsset = SecuritySpec.fromConfig("controllers.AssetApi.updateAsset", AdminSpec)
    val UpdateAssetForMaintenance =
      SecuritySpec.fromConfig("controllers.AssetApi.updateAssetForMaintenance", AdminSpec)
  }

  object AssetManagementApi {
    val p = "controllers.AssetManagementApi"
    def b(s: String) = { if (s.isEmpty) p else "%s.%s".format(p, s) }
    val Spec = SecuritySpec.fromConfig(b(""), LoggedIn)
    val PowerStatus = SecuritySpec.fromConfig(b("powerStatus"), Spec)
    val PowerManagement = SecuritySpec.fromConfig(b("powerManagement"), AdminSpec)
  }

  object AssetWebApi {
    val Spec = SecuritySpec.fromConfig("controllers.AssetWebApi", AdminSpec)
    val CancelAsset = SecuritySpec.fromConfig("controllers.AssetWebApi.cancelAsset", Spec)
    val ProvisionAsset = SecuritySpec.fromConfig("controllers.AssetWebApi.provisionAsset", Spec)
  }

}
