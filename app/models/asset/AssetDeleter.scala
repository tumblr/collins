package models
package asset

import util.plugins.{Cache, Callback}

import org.squeryl.PrimitiveTypeMode._

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
      Callback.fire("asset_purge", tag, null)
      Cache.clear()
    }
    result
  }

  protected def deleteMetaValues(asset: Asset): Boolean = {
    AssetMetaValue.deleteByAsset(asset) >= 0
  }
  protected def deleteLogs(asset: Asset): Boolean = {
    AssetLog.tableDef.deleteWhere(al => al.asset_id === asset.id) >= 0
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
