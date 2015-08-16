package collins.models.asset

import org.squeryl.PrimitiveTypeMode._

import collins.models.cache.Cache

import collins.models.Asset
import collins.models.Asset
import collins.models.AssetLog
import collins.models.AssetMetaValue
import collins.models.IpAddresses
import collins.models.IpmiInfo

import collins.callbacks.Callback
import collins.callbacks.StringDatum
import collins.callbacks.CallbackDatumHolder

object AssetDeleter {
  def purge(asset: Asset): Boolean = {
    val tag = asset.tag
    val result = Asset.inTransaction {
      deleteMetaValues(asset) &&
        deleteLogs(asset) &&
        deleteIpmiInfo(asset) &&
        deleteIpAddresses(asset) &&
        deleteAsset(asset)
    }
    if (result) {
      Callback.fire("asset_purge", CallbackDatumHolder(Some(StringDatum(tag))), CallbackDatumHolder(None))
      Cache.clear()
    }
    result
  }

  protected def deleteMetaValues(asset: Asset): Boolean = {
    AssetMetaValue.deleteByAsset(asset) >= 0
  }
  protected def deleteLogs(asset: Asset): Boolean = {
    AssetLog.tableDef.deleteWhere(al => al.assetId === asset.id) >= 0
  }
  protected def deleteIpmiInfo(asset: Asset): Boolean = {
    IpmiInfo.deleteByAsset(asset) >= 0
  }
  protected def deleteIpAddresses(asset: Asset): Boolean = {
    IpAddresses.deleteByAsset(asset) >= 0
  }
  protected def deleteAsset(asset: Asset): Boolean = {
    Asset.delete(asset) >= 0
  }
}

