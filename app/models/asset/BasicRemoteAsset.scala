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

  private[this] def warnAboutData(name: String): Option[String] = {
    logger.warn("Attempting to retrieve %s attribute on basic remote asset, returning nothing".format(name))
    None
  }

  def getHostnameMetaValue() = warnAboutData("HOSTNAME")
  def getPrimaryRoleMetaValue() = warnAboutData("PRIMARY_ROLE")
  def getMetaAttributeValue(name: String) = warnAboutData(name)
}

