package util
package views

import collins.action.Action
import models.{Asset, AssetMeta, Page}
import models.asset.AssetView
import util.plugins.SoftLayer
import util.power.{PowerComponent, PowerUnits}

import play.api.Logger


// Mostly used with views/asset/list, also for comprehensions
object ListHelper {

  protected val logger = Logger("ListHelper")

  /**
   * Returns the header for the column of asset metadata dictated by a metadata
   * tag.  If a metadata tag, retrieves tag's label and returns, otherwise uses
   * the optional header configuration value, specified by the Collins
   * configuration.  Failing this, returns the tag/method call passed in.
   *
   * @param tag a String containing a metadata tag or method call
   * @return a String containing the column header for this tag/method call
   */
  def getColumnHeader(tag: String): String = {
    AssetMeta.findByName(tag) match {
      case Some(meta) => return meta.label
      case _ =>
    }
    val configuredHeader = TagDecoratorConfig.getHeader(tag)
    if (!configuredHeader.isEmpty) {
      return configuredHeader
    }
    tag
  }

  /**
   * Returns an ordered list of the asset tags to use when listing assets, as
   * specified within the Collins configuration file.
   *
   * @return an ordered list of Strings containing tags from assets
   */
  def getListHeaderTags(): Seq[String] = {
    ListConfig.defaultTagOrder
  }

  /**
   * Returns the value of an asset's metadata tag, returning None if no value
   * is defined.
   *
   * @param tag a String containing an asset metadata tag or asset method call
   * @return the value of the asset metadata tag or asset method call.
   */
  def getTagValueForAsset(tag: String, asset: AssetView) = {
    val metaValue = Asset.findByTag(asset.tag).get.getMetaAttribute(tag)
    if (metaValue != None) {
      metaValue.get.getValue
    } else {
      None
    }
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

  /**
   * Returns whether an asset's metadata tag's header should be shown.
   *
   * @param tag a String containing an asset's metadata tag.
   * @param assets a Page of AssetView objects, representing the assets found.
   * @return a Boolean representing whether this column should be shown.
   */
  def showColumnForTag(tag: String, assets: Page[AssetView]): Boolean = {
    TagDecoratorConfig.getShowIf(tag) match {
      case Some(actionConfig) => {
        logger.debug("Found showIf for %s; type: %s, command: %s".format(tag,
          actionConfig.actionType, actionConfig.command.mkString(", ")))
        val executor = Action.getExecutor(actionConfig)
        return executor.checkAssetsAction(assets)
      }
      case None => {
        // If the tag is not present within the db/cache, don't display column.
        if (AssetMeta.findByName(tag) == None) {
          logger.debug("Found no AssetMeta for %s".format(tag))
          return true
        }
      }
    }
    true
  }

}
