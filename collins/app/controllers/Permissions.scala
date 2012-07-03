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

  object Feature extends PermSpec("feature") {
    val CanSeePasswords = spec("canSeePasswords", AdminSpec)
    val NoRateLimit = spec("noRateLimit", AdminSpec)
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

  object AssetLogApi extends PermSpec("controllers.AssetLogApi") {
    val Spec = spec(AdminSpec)
    val Create = spec("submitLogData", Spec)
    val Get = spec("getLogData", LoggedIn)
    val GetAll = spec("getAllLogData", Spec)
  }

  object AssetManagementApi extends PermSpec("controllers.AssetManagementApi") {
    val Spec = spec(LoggedIn)
    val PowerStatus = spec("powerStatus", Spec)
    val PowerManagement = spec("powerManagement", AdminSpec)
    val ProvisionAsset = spec("provisionAsset", AdminSpec)
    val GetProvisioningProfiles = spec("getProvisioningProfiles", AdminSpec)
  }

  object AssetWebApi extends PermSpec("controllers.AssetWebApi") {
    val Spec = spec(AdminSpec)
    val CancelAsset = spec("cancelAsset", Spec)
  }

  object Help extends PermSpec("controllers.Help") {
    val Spec = spec(LoggedIn)
    val Index = spec("index", Spec)
  }

  object IpAddressApi extends PermSpec("controllers.IpAddressApi") {
    val Spec = spec(AdminSpec)
    val AllocateAddress = spec("allocateAddress", Spec)
    val AssetFromAddress = spec("assetFromAddress", LoggedIn)
    val AssetsFromPool = spec("assetsFromPool", LoggedIn)
    val GetForAsset = spec("getForAsset", LoggedIn)
    val GetAddressPools = spec("getAddressPools", LoggedIn)
    val UpdateAddress = spec("updateAddress", Spec)
    val PurgeAddresses = spec("purgeAddresses", Spec)
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
