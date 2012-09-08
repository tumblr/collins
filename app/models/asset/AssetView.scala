package models.asset

import models.Status
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
  def getStatusName(): String = Status.findById(status).map(_.name).getOrElse("Unknown")

  def remoteHost: Option[String] //none if local

  def toJsValue(): JsValue
}
