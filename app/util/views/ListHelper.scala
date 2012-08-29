package util
package views

import play.api.Configuration

import models.{Asset, AssetMeta, AssetView, Page}
import util.power.{PowerComponent, PowerUnits}

case class FormatConfigurationException(formatter: String, tag: String)
  extends Exception("Didn't find formatter %s in configuration for %s".format(
      formatter, tag))

case class ShowIfConfigurationException(method: String, tag: String)
  extends Exception("Didn't find method ListHelperBase.%s in configuration for %s".format(
      method, tag))

/**
 * Helper methods used when compiling lists of assets within Collins, with
 * facilities for determining whether tags should be shown, and how to display
 * information from these tags.
 */
object ListHelper extends DecoratorBase {

  /**
   * Specifies the default ordering for asset list views.
   */
  val DEFAULT_TAG_ORDER = "ASSET_TAG,HOSTNAME,PRIMARY_ROLE,STATUS,CREATED,LAST_UPDATED"

  /**
   * Supplies a configuration prefix to all decorator-specific methods.
   *
   * @return a String containing the list tag-specific configuration prefix.
   */
  override def configPrefix(): String = "listtags"

  /**
   * Returns an ordered list of the asset tags to use when listing assets, as
   * specified within the Collins configuration file.
   *
   * @return an ordered list of Strings containing tags from assets
   */
  def getListHeaderTags(): Seq[String] = {
    Config.getString("listtags.order", DEFAULT_TAG_ORDER).split(",")
  }

  /**
   * Formats the value of an asset's metadata tag by the formatter specified
   * within the Collins configuration file.
   *
   * @param tag a String containing an asset metadata tag.
   * @param value an Object containing the value of an asset metadata tag.
   * @return a formatted string containing the metadata tag's value.
   */
  def formatTagValue(tag: String, value: Object): String = {
    val formatter = Config.getString("listtags.%s.formatter".format(tag), "")
    if (!formatter.isEmpty() && value != null) {
      try {
        Formatter.getClass.getMethod(formatter, value.getClass).invoke(
            Formatter, value).asInstanceOf[String]
      } catch {
        case nsme: NoSuchMethodException =>
          throw FormatConfigurationException(formatter, tag)
      }
    } else {
      value.asInstanceOf[String]
    }
  }

  /**
   * Returns whether an asset's metadata tag's header should be shown.
   *
   * @param tag a String containing an asset's metadata tag.
   * @param assets a Page of AssetView objects, representing the assets found.
   * @return a Boolean representing whether this column should be shown.
   */
  def showColumnForTag(tag: String, assets: Page[AssetView]): Boolean = {
    val method = Config.getString("listtags.%s.showif".format(tag), "")
    if (!method.isEmpty()) {
      try {
        this.getClass.getMethod(method, assets.getClass).invoke(this, assets)
          .asInstanceOf[Boolean]
      } catch {
        case nsme: NoSuchMethodException =>
          throw ShowIfConfigurationException(method, tag)
      }
    } else {
      true
    }
  }

  /**
   * Returns whether the Hostname column should be shown for a list of assets.
   *
   * @param assets a Page of AssetView objects, representing the assets to be
   *   listed.
   * @return a Boolean representing whether the Hostname column should be shown.
   */
  def showHostname(assets: Page[AssetView]): Boolean = {
    assets.items.find(_.getHostnameMetaValue.isDefined).map(_ => true).getOrElse(false)
  }

  def showSoftLayerLink(assets: Page[AssetView]): Boolean = {
    SoftLayer.pluginEnabled { plugin =>
      assets.items.collectFirst{ case asset: Asset if(plugin.isSoftLayerAsset(asset)) => true }.getOrElse(false)
    }.getOrElse(false)
  }

  def getPowerComponentsInOrder(units: PowerUnits): Seq[PowerComponent] = {
    val components = units.flatMap { unit =>
      unit.components
    }
    components.toSeq.sorted
  }

  def getPowerComponentsInOrder(): Seq[PowerComponent] = {
    getPowerComponentsInOrder(PowerUnits())
  }

}
