package models.asset

import models.{AssetType, Status}
import play.api.libs.json.JsValue
import java.sql.Timestamp

/**
 * An AssetView can be either a regular Asset or a RemoteAsset from another
 * collins instance.  This interface should only expose methods needed by the
 * list view, since from there the client is directed to whichever instnace
 * actually owns the asset.
 */
trait AssetView {

  def id: Long
  def tag: String
  def status: Int
  def asset_type: Int
  def created: Timestamp
  def updated: Option[Timestamp]
  def deleted: Option[Timestamp]

  def getHostnameMetaValue(): Option[String]
  def getPrimaryRoleMetaValue(): Option[String]
  def remoteHost: Option[String] //none if local
  def toJsValue(): JsValue

  def getStatusName(): String = Status.findById(status).map(_.name).getOrElse("Unknown")

  def isServerNode(): Boolean = asset_type == AssetType.Enum.ServerNode.id
  def isConfiguration(): Boolean = asset_type == AssetType.Enum.Config.id

  // yellow
  def isIncomplete(): Boolean = status == Status.Enum.Incomplete.id
  def isNew(): Boolean = status == Status.Enum.New.id

  def isUnallocated(): Boolean = status == Status.Enum.Unallocated.id

  // blue
  def isProvisioning(): Boolean = status == Status.Enum.Provisioning.id
  def isProvisioned(): Boolean = status == Status.Enum.Provisioned.id

  def isAllocated(): Boolean = status == Status.Enum.Allocated.id

  // green
  def isCancelled(): Boolean = status == Status.Enum.Cancelled.id
  def isDecommissioned(): Boolean = status == Status.Enum.Decommissioned.id

  // red
  def isMaintenance(): Boolean = status == Status.Enum.Maintenance.id
}
