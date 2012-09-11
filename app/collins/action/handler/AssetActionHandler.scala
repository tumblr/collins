package collins
package action
package handler

import action.ActionExecutor
import action.FormattedValues
import models.AssetView

import play.api.Logger


trait AssetActionHandler extends ActionExecutor {

  def formatAsset(asset: AssetView): FormattedValues = {
    getTemplatedCommand(asset)
  }

  def checkAsset(asset: AssetView): Boolean = {
    runCommandBoolean(getTemplatedCommand(asset)).asInstanceOf[Boolean]
  }

  def executeAsset(asset: AssetView): String = {
    runCommandString(getTemplatedCommand(asset))
  }

}
