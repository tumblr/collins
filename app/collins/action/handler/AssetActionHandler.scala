package collins
package action
package handler

import action.{ActionExecutor, FormattedValues}
import models.asset.AssetView

import play.api.Logger


trait AssetActionHandler extends ActionExecutor {

  def formatAsset(asset: AssetView): FormattedValues = {
    getTemplatedCommand(asset)
  }

  def checkAsset(asset: AssetView): Boolean = {
    runCommandBoolean(getTemplatedCommand(asset))
  }

  def executeAsset(asset: AssetView): String = {
    runCommandString(getTemplatedCommand(asset))
  }

}
