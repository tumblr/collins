package util
package views

import play.api.Configuration
import play.api.mvc.Content
import play.api.templates.Html

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
  val DEFAULT_TAG_ORDER = "~tag,HOSTNAME,PRIMARY_ROLE,STATUS,~created,~updated"

  /**
   * Specifies a prefix which indicates to call a method instead of performing
   * a tag lookup.
   */
  val METHOD_PREFIX = "~"

  /**
   * Supplies a configuration prefix to all decorator-specific methods.
   *
   * @return a String containing the list tag-specific configuration prefix.
   */
  override def configPrefix(): String = "listtags"

  def getColumnHeader(tag: String): String = {
    if (!tag.contains(METHOD_PREFIX)) {
      val assetMeta = AssetMeta.findByName(tag)
      if (assetMeta != None) {
        assetMeta.get.getLabel()
      } else {
        tag
      }
    } else {
      val header = Config.getString("listtags.%s.header".format(tag), "")
      if (!header.isEmpty()) {
        header
      } else {
        tag
      }
    }
  }

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
   * Formats the text value of an asset's metadata tag by the configuration
   * specified within the Collins config file.
   *
   * @param tag a String containing an asset metadata tag.
   * @param asset the AssetView corresponding to the asset.
   * @return a formatted String containing the metadata tag's value.
   */
  def formatTagValueForAsset(tag: String, asset: AssetView): String = {
    val tagValue = getTagValueForAsset(tag, asset)
    val formatter = Config.getString("listtags.%s.formatter".format(tag), "")
    if (!formatter.isEmpty() && !tagValue.isEmpty()) {
      try {
        Formatter.getClass.getMethod(formatter, tagValue.getClass).invoke(
            Formatter, tagValue).toString()
      } catch {
        case nsme: NoSuchMethodException =>
          throw FormatConfigurationException(formatter, tag)
      }
    } else {
      tagValue
    }
  }

  def getTagValueForAsset(tag: String, asset: AssetView) = {
    // If the tag starts with the method prefix, call the method instead of
    // performing an asset metadata lookup.
    if (tag.contains(METHOD_PREFIX)) {
      asset.getClass.getMethod(tag.split(METHOD_PREFIX)(1)).invoke(asset)
        .toString()
    } else {
      val metaValue = Asset.findByTag(asset.tag).get.getMetaAttribute(tag)
      if (metaValue != None) {
        metaValue.get.getValue
      } else {
        ""
      }
    }
  }

  /**
   * Formats and decorates the value of an asset's metadata tag for HTML
   * display by the configuration specified within the Collins config file.
   *
   * @param tag a String containing an asset metadata tag.
   * @param asset the AssetView corresponding to the asset.
   * @return a Play Content object containing the metadata tag's value.
   */
  def decorateTagValueForAsset(tag: String, asset: AssetView): Content = {
    val formattedVal = formatTagValueForAsset(tag, asset)
    val decorator = Config.getString("listtags.%s.decorator".format(tag), "")
    if (formattedVal.isEmpty()) {
      val default = Config.getString("listtags.%s.default".format(tag), "")
      if (!default.isEmpty()) {
        Html(default)
      } else {
        Html("<em>Undefined</em>")
      }
    } else if (!decorator.isEmpty()) {
      decorate(tag, formattedVal)
    } else {
      Html(formattedVal)
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
    if (!tag.contains(METHOD_PREFIX)) {
      // If the tag is not present within the db/cache, don't display column.
      if (AssetMeta.findByName(tag) == None) {
        return false
      }
    }
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
