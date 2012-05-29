package util
package views

import models.{Asset, Page}
import util.power.{PowerComponent, PowerUnits}

// Mostly used with views/asset/list, also for comprehensions
object ListHelper {
  def showHostname(assets: Page[Asset]): Boolean = {
    assets.items.find(_.getMetaAttribute("HOSTNAME").isDefined).map(_ => true).getOrElse(false)
  }
  def showSoftLayerLink(assets: Page[Asset]): Boolean = {
    SoftLayer.pluginEnabled { plugin =>
      assets.items.find(asset => plugin.isSoftLayerAsset(asset)).map(_ => true).getOrElse(false)
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
