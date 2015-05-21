package collins.controllers

import collins.models.User
import collins.util.security.AuthenticationProvider
import collins.util.security.AuthenticationProviderConfig
import collins.util.security.SecuritySpec
import collins.util.security.SecuritySpecification

object Permissions {
  val LoggedIn = SecuritySpec.fromConfig("controllers.Api", SecuritySpec(true))
  def AdminSpec = SecuritySpec(true, AuthenticationProviderConfig.adminGroup)

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
    def CanSeePasswords = spec("canSeePasswords", AdminSpec)
    def NoRateLimit = spec("noRateLimit", AdminSpec)
  }

  object Admin extends PermSpec("controllers.Admin") {
    def Spec = spec(AdminSpec)
    def Stats = spec("stats", Spec)
    def ClearCache = spec("clearCache", Stats)
  }

  object AssetApi extends PermSpec("controllers.AssetApi") {
    def Spec = spec(LoggedIn)
    def CreateAsset = spec("createAsset", AdminSpec)
    def DeleteAsset = spec("deleteAsset", AdminSpec)
    def DeleteAssetAttribute = spec("deleteAssetAttribute", AdminSpec)
    def GetAsset = spec("getAsset", Spec)
    def GetAssets = spec("getAssets", Spec)
    def UpdateAsset = spec("updateAsset", AdminSpec)
    def UpdateAssetForMaintenance = spec("updateAssetForMaintenance", AdminSpec)
    def UpdateAssetStatus = spec("updateAssetStatus", AdminSpec)
  }

  object AssetTypeApi extends PermSpec("controllers.AssetTypeApi") {
    def Spec = spec(LoggedIn)
    def GetAssetTypes = spec("getAssetTypes", Spec)
    def CreateAssetType = spec("createAssetType", AdminSpec)
    def UpdateAssetType = spec("updateAssetType", AdminSpec)
    def DeleteAssetType = spec("deleteAssetType", AdminSpec)
  }

  object AssetLogApi extends PermSpec("controllers.AssetLogApi") {
    def Spec = spec(AdminSpec)
    def Create = spec("submitLogData", Spec)
    def Get = spec("getLogData", LoggedIn)
    def GetAll = spec("getAllLogData", Spec)
  }

  object AssetManagementApi extends PermSpec("controllers.AssetManagementApi") {
    def Spec = spec(LoggedIn)
    def PowerStatus = spec("powerStatus", Spec)
    def PowerManagement = spec("powerManagement", AdminSpec)
    def ProvisionAsset = spec("provisionAsset", AdminSpec)
    def GetProvisioningProfiles = spec("getProvisioningProfiles", AdminSpec)
  }

  object AssetStateApi extends PermSpec("controllers.AssetStateApi") {
    def Spec = spec(AdminSpec)
    def Create = spec("createState", Spec)
    def Delete = spec("deleteState", Spec)
    def Get = spec("getState", LoggedIn)
    def Update = spec("updateState", Spec)
  }

  object AssetWebApi extends PermSpec("controllers.AssetWebApi") {
    def Spec = spec(AdminSpec)
    def CancelAsset = spec("cancelAsset", Spec)
    def DeleteAsset = spec("deleteAsset", Spec)
  }

  object Help extends PermSpec("controllers.Help") {
    def Spec = spec(LoggedIn)
    def Index = spec("index", Spec)
  }

  object IpmiApi extends PermSpec("controllers.IpmiApi") {
    def Spec = spec(AdminSpec)
    def UpdateIpmi = spec("updateIpmi", Spec)
  }

  object IpAddressApi extends PermSpec("controllers.IpAddressApi") {
    def Spec = spec(AdminSpec)
    def AllocateAddress = spec("allocateAddress", Spec)
    def AssetFromAddress = spec("assetFromAddress", LoggedIn)
    def AssetsFromPool = spec("assetsFromPool", LoggedIn)
    def GetForAsset = spec("getForAsset", LoggedIn)
    def GetAddressPools = spec("getAddressPools", LoggedIn)
    def UpdateAddress = spec("updateAddress", Spec)
    def PurgeAddresses = spec("purgeAddresses", Spec)
  }

  object Resources extends PermSpec("controllers.Resources") {
    def Spec = spec(LoggedIn)
    def CreateAsset = spec("createAsset", AdminSpec)
    def CreateForm = spec("displayCreateForm", AdminSpec)
    def Find = spec("find", Spec)
    def Index = spec("index", Spec)
    def Intake = spec("intake", AdminSpec)
  }

  object TagApi extends PermSpec("controllers.TagApi") {
    def Spec = spec(LoggedIn)
    def GetTags = spec("getTags", Spec)
    def GetTagValues = spec("getTagValues", AdminSpec)
  }

}
