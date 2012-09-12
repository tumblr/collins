package models.asset

import models.{Asset, Status}
import conversions._
import play.api.Logger
import play.api.libs.json._

/**
 * A remote asset that extracts from json returned by collins when details is false
 */
case class BasicRemoteAsset(hostTag: String, remoteUrl: String, json: JsObject)
  extends RemoteAssetProxy(json)
{
  private[this] val logger = Logger("BasicRemoteAsset")

  private[this] def warnAboutData(): Option[String] = {
    logger.warn("Attempting to retrieve details data on basic remote asset")
    None
  }

  def getHostnameMetaValue() = warnAboutData()
  def getPrimaryRoleMetaValue() = warnAboutData()
}
 
