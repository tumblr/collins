package util
package views

import config.Configurable
import util.config.ActionConfig


object SearchResultsConfig extends Configurable {

  override val namespace = "searchresults"
  override val referenceConfigFilename = "searchresults_reference.conf"

  def defaultTagOrder = {
    val tagList = getStringList("defaultTagOrder")
    if (tagList.isEmpty) {
      List("TAG", "HOSTNAME", "PRIMARY_ROLE", "STATUS", "CREATED", "UPDATED")
    } else {
      tagList
    }
  }

  def rowClassAction: Option[ActionConfig] =
    ActionConfig.getActionConfig(getConfig("rowClassAction"))

  override def validateConfig() {
    defaultTagOrder
  }

}
