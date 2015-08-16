package collins.callbacks

import org.specs2.mutable
import org.specs2.specification.Scope

import play.api.test.FakeApplication
import play.api.test.WithApplication
import play.api.libs.concurrent.AkkaPlugin

import collins.util.IpAddress

import collins.models.Asset
import collins.models.AssetLog
import collins.models.AssetMetaValue
import collins.models.IpAddresses
import collins.models.AssetType
import collins.models.Status
import collins.models.State
import collins.models.Asset
import collins.models.AssetLog
import collins.models.AssetMeta
import collins.models.AssetMetaValue
import collins.models.IpAddresses

class CallbackSpec extends mutable.Specification {

  "Callbacks" should {
    "be invoked on asset create" in new WithApplication(FakeApplication(
      additionalConfiguration = Map(
        "callbacks.enabled" -> "true"))) {

      ExecStats.reset()
      val assetTag = "tumblrtag2"
      val assetStatus = Status.Incomplete.get
      val assetType = AssetType.ServerNode.get
      val newAsset = Asset(assetTag, assetStatus, assetType)

      // create an asset
      val result = Asset.create(newAsset)
      result.id must beGreaterThan(1L)

      // update the asset
      val maybeAsset = Asset.findByTag(assetTag)
      maybeAsset must beSome[Asset]

      // sleep for the actors to complete execution
      Thread.sleep(500)
      // 2 callbacks must be invoked, test config specifies one for onCreate and test specified one for update
      ExecStats.totalCount.longValue() must beEqualTo(1)
    }

    "be invoked on asset update" in new WithApplication(FakeApplication(
      additionalConfiguration = Map(
        "callbacks.enabled" -> "true",
        "callbacks.registry.onMaintenanced.on" -> "asset_update",
        "callbacks.registry.onMaintenanced.when.previous.state" -> "!isMaintenance",
        "callbacks.registry.onMaintenanced.when.current.state" -> "isMaintenance",
        "callbacks.registry.onMaintenanced.action.type" -> "exec",
        "callbacks.registry.onMaintenanced.action.command" -> """print "Maintenanced"""",
        "callbacks.registry.onCancellation.on" -> "asset_update",
        "callbacks.registry.onCancellation.when.previous.state" -> "!isCancelled",
        "callbacks.registry.onCancellation.when.current.state" -> "isCancelled",
        "callbacks.registry.onCancellation.action.type" -> "exec",
        "callbacks.registry.onCancellation.action.command" -> """print "Cancelled""""))) {

      ExecStats.reset()
      val assetTag = "tumblrtag2"
      val assetStatus = Status.Incomplete.get
      val assetType = AssetType.ServerNode.get
      val newAsset = Asset(assetTag, assetStatus, assetType)

      // create an asset
      val result = Asset.create(newAsset)
      result.id must beGreaterThan(1L)

      // update the asset
      val maybeAsset = Asset.findByTag(assetTag)
      maybeAsset must beSome[Asset]
      val realAsset = maybeAsset.get
      Asset.update(realAsset.copy(statusId = Status.Maintenance.get.id))

      // sleep for the actors to complete execution
      Thread.sleep(500)

      // 2 callbacks must be invoked, dev config specifies one for onCreate and test specified one for update
      ExecStats.totalCount.longValue() must beEqualTo(2)

      // update asset to cancelled, but previous.state check is successful
      ExecStats.reset()
      Asset.update(realAsset.copy(statusId = Status.Cancelled.get.id))
      // sleep for the actors to complete execution
      Thread.sleep(500)
      ExecStats.totalCount.longValue() must beEqualTo(1)
    }

    "fire for all callbacks" in new WithApplication(FakeApplication(
      additionalConfiguration = Map(
        "callbacks.enabled" -> "true",
        "callbacks.registry.onMaintenanced.on" -> "asset_update",
        "callbacks.registry.onMaintenanced.when.previous.state" -> "!isMaintenance",
        "callbacks.registry.onMaintenanced.when.current.state" -> "isMaintenance",
        "callbacks.registry.onMaintenanced.action.type" -> "exec",
        "callbacks.registry.onMaintenanced.action.command" -> """print "Maintenanced""""))) {

      ExecStats.reset()
      val assetTag = "tumblrtag2"
      val assetStatus = Status.Incomplete.get
      val assetType = AssetType.ServerNode.get
      val newAsset = Asset(assetTag, assetStatus, assetType)

      // create an asset
      val result = Asset.create(newAsset)
      result.id must beGreaterThan(1L)

      // update the asset
      val maybeAsset = Asset.findByTag(assetTag)
      maybeAsset must beSome[Asset]
      val realAsset = maybeAsset.get
      Asset.update(realAsset.copy(statusId = Status.Maintenance.get.id, stateId = State.findByName("IPMI_PROBLEM").get.id))

      // sleep for the actors to complete execution
      Thread.sleep(500)
      // 3 callbacks must be invoked, test config specifies one for onCreate
      // one for update and test specified one for update on IPMI_PROBLEM
      ExecStats.totalCount.longValue() must beEqualTo(3)
    }

    "be invoked for configuration assets" in new WithApplication(FakeApplication(
      additionalConfiguration = Map(
        "callbacks.enabled" -> "true",
        "callbacks.registry.configAsset.on" -> "asset_update",
        "callbacks.registry.configAsset.when.previous.state" -> "!isAllocated",
        "callbacks.registry.configAsset.when.current.state" -> "isAllocated",
        "callbacks.registry.configAsset.action.type" -> "exec",
        "callbacks.registry.configAsset.action.command" -> """print "Config Asset""""))) {

      ExecStats.reset()
      val assetTag = "configasset1"
      val assetStatus = Status.Incomplete.get
      val assetType = AssetType.Configuration.get
      val newAsset = Asset(assetTag, assetStatus, assetType)

      // create config asset
      val result = Asset.create(newAsset)
      result.id must beGreaterThan(1L)

      // update the asset
      val maybeAsset = Asset.findByTag(assetTag)
      maybeAsset must beSome[Asset]
      val realAsset = maybeAsset.get
      Asset.update(realAsset.copy(statusId = Status.Allocated.get.id))

      // sleep for the actors to complete execution
      Thread.sleep(500)
      // 2 callbacks must be invoked, test config specifies one for onCreate and test specified one for update
      ExecStats.totalCount.longValue() must beEqualTo(2)
    }

    "be supported on Asset Logs for create events" in new WithApplication(FakeApplication(
      additionalConfiguration = Map(
        "callbacks.enabled" -> "true",
        "callbacks.registry.assetlog.on" -> "asset_log_create",
        "callbacks.registry.assetlog.action.type" -> "exec",
        "callbacks.registry.assetlog.action.command" -> """print "Asset Log""""))) {

      ExecStats.reset()
      val assetTag = "configasset1"
      val assetStatus = Status.Incomplete.get
      val assetType = AssetType.Configuration.get
      val newAsset = Asset(assetTag, assetStatus, assetType)

      // create config asset
      val result = Asset.create(newAsset)
      result.id must beGreaterThan(1L)

      // create the asset log
      val log = new AssetLog().copy(assetId = result.id, message = "A log message")
      AssetLog.create(log)

      // sleep for the actors to complete execution
      Thread.sleep(500)
      // 2 callbacks must be invoked, test config specifies one for onCreate for asset test specifies one for asset log
      ExecStats.totalCount.longValue() must beEqualTo(2)
    }

    "be supported on asset meta values for create and delete events" in new WithApplication(FakeApplication(
      additionalConfiguration = Map(
        "callbacks.enabled" -> "true",
        "callbacks.registry.amvc.on" -> "asset_meta_value_create",
        "callbacks.registry.amvc.action.type" -> "exec",
        "callbacks.registry.amvc.action.command" -> """print "Create Asset Meta Value"""",
        "callbacks.registry.amvd.on" -> "asset_meta_value_delete",
        "callbacks.registry.amvd.action.type" -> "exec",
        "callbacks.registry.amvd.action.command" -> """print "Delete Asset Meta Value""""))) {

      ExecStats.reset()

      val assetTag = "configasset1"
      val assetStatus = Status.Incomplete.get
      val assetType = AssetType.ServerNode.get
      val newAsset = Asset(assetTag, assetStatus, assetType)

      // create an asset
      val result = Asset.create(newAsset)
      result.id must beGreaterThan(1L)

      val meta = AssetMeta.findOrCreateFromName("M1")
      AssetMetaValue.delete(AssetMetaValue.create(new AssetMetaValue(newAsset.id, meta.id, 0, "V1")))

      // sleep for the actors to complete execution
      Thread.sleep(500)
      // 3 callbacks must be invoked, test config specifies one for onCreate
      // for asset test specifies one each for asset meta value create and delete
      ExecStats.totalCount.longValue() must beEqualTo(3)
    }

    "be supported on ip addresses for create, update and delete events" in new WithApplication(FakeApplication(
      additionalConfiguration = Map(
        "callbacks.enabled" -> "true",
        "callbacks.registry.ipac.on" -> "ipAddresses_create",
        "callbacks.registry.ipac.action.type" -> "exec",
        "callbacks.registry.ipac.action.command" -> """print "Create IP Address"""",
        "callbacks.registry.ipau.on" -> "ipAddresses_update",
        "callbacks.registry.ipau.action.type" -> "exec",
        "callbacks.registry.ipau.action.command" -> """print "Update IP Address"""",
        "callbacks.registry.ipad.on" -> "ipAddresses_delete",
        "callbacks.registry.ipad.action.type" -> "exec",
        "callbacks.registry.ipad.action.command" -> """print "Delete IP Address""""))) {

      ExecStats.reset()

      val assetTag = "configasset1"
      val assetStatus = Status.Incomplete.get
      val assetType = AssetType.ServerNode.get
      val newAsset = Asset(assetTag, assetStatus, assetType)

      // create an asset
      val result = Asset.create(newAsset)
      result.id must beGreaterThan(1L)

      // create the address
      val asset = Asset.findById(1).get
      val address = IpAddresses.create(IpAddresses(asset.id, IpAddress.toLong("10.0.0.1"),
        IpAddress.toLong("10.0.0.3"), IpAddress.toLong("255.255.224.0"), "fortesting"))

      // update the address
      IpAddresses.update(address.copy(gateway = IpAddress.toLong("10.0.0.2")))

      // delete the address
      IpAddresses.delete(address)

      // sleep for the actors to complete execution
      Thread.sleep(500)
      // 3 callbacks must be invoked, test config specifies one for onCreate
      // for asset test specifies one each for asset meta value create and delete
      ExecStats.totalCount.longValue() must beEqualTo(4)
    }
  }
}