package collins.models.asset

import java.sql.Timestamp

import play.api.libs.json.JsValue

import collins.models.AssetType
import collins.models.AssetType
import collins.models.Status
import collins.models.Status

/**
 * An AssetView can be either a regular Asset or a RemoteAsset from another
 * collins instance.  This interface should only expose methods needed by the
 * list view, since from there the client is directed to whichever instnace
 * actually owns the asset.
 */
trait AssetView {

  def id: Long
  def tag: String
  def statusId: Int
  def stateId: Int
  def assetTypeId: Int
  def created: Timestamp
  def updated: Option[Timestamp]
  def deleted: Option[Timestamp]

  def getHostnameMetaValue(): Option[String]
  def getPrimaryRoleMetaValue(): Option[String]
  def getMetaAttributeValue(name: String): Option[String]
  def remoteHost: Option[String] //none if local
  def toJsValue(): JsValue

  def getStatusName(): String
  def getStateName(): String
  def getTypeName(): String

  def isServerNode(): Boolean = isAssetType(AssetType.ServerNode)
  def isConfiguration(): Boolean = isAssetType(AssetType.Configuration)

  // Status values
  def isIncomplete(): Boolean = isStatus(Status.Incomplete)
  def isNew(): Boolean = isStatus(Status.New)
  def isUnallocated(): Boolean = isStatus(Status.Unallocated)
  def isProvisioning(): Boolean = isStatus(Status.Provisioning)
  def isProvisioned(): Boolean = isStatus(Status.Provisioned)
  def isAllocated(): Boolean = isStatus(Status.Allocated)
  def isCancelled(): Boolean = isStatus(Status.Cancelled)
  def isDecommissioned(): Boolean = isStatus(Status.Decommissioned)
  def isMaintenance(): Boolean = isStatus(Status.Maintenance)

  private def isStatus(statusOpt: Option[Status]): Boolean = {
    statusOpt.map(_.id).filter(_.equals(statusId)).isDefined
  }
  protected def isAssetType(atOpt: Option[AssetType]): Boolean = {
    atOpt.map(_.id).filter(_.equals(assetTypeId)).isDefined
  }
}
