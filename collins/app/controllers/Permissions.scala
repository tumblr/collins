package controllers

import models.User
import util._

object Permissions {
  val LoggedIn = SecuritySpec.fromConfig("controllers.Api", SecuritySpec(true))
  val AdminSpec = SecuritySpec(true, AppConfig.adminGroup)

  def please(user: User, spec: SecuritySpecification): Boolean = {
    AuthenticationProvider.userIsAuthorized(user, spec)
  }

  class PermSpec(val klass: String) {
    def spec(method: String, default: SecuritySpecification): SecuritySpecification = {
      val fmt = "%s.%s".format(klass, method)
      SecuritySpec.fromConfig(fmt, default)
    }
    def spec(default: SecuritySpecification): SecuritySpecification = {
      SecuritySpec.fromConfig(klass, default)
    }
  }

  object Admin extends PermSpec("controllers.Admin") {
    val Spec = spec(AdminSpec)
    val Stats = spec("stats", Spec)
    val ClearCache = spec("clearCache", Stats)
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

  object AssetLogApi {
    val Spec = SecuritySpec.fromConfig("controllers.AssetLogApi", LoggedIn);
    val Create = SecuritySpec.fromConfig("controllers.AssetLogApi.submitLogData", AdminSpec);
    val Get = SecuritySpec.fromConfig("controllers.AssetLogApi.getLogData", Spec);
    val GetAll = SecuritySpec.fromConfig("controllers.AssetLogApi.getAllLogData", Spec);
  }

  object AssetManagementApi extends PermSpec("controllers.AssetManagementApi") {
    val Spec = spec(LoggedIn)
    val PowerStatus = spec("powerStatus", Spec)
    val PowerManagement = spec("powerManagement", AdminSpec)
  }

  object AssetWebApi extends PermSpec("controllers.AssetWebApi") {
    val Spec = spec(AdminSpec)
    val CancelAsset = spec("cancelAsset", Spec)
    val ProvisionAsset = spec("provisionAsset", Spec)
  }

  object Resources extends PermSpec("controllers.Resources") {
    val Spec = spec(LoggedIn)
    val CreateAsset = spec("createAsset", AdminSpec)
    val CreateForm = spec("displayCreateForm", AdminSpec)
    val Find = spec("find", Spec)
    val Index = spec("index", Spec)
    val Intake = spec("intake", AdminSpec)
  }

}
