package util
package views

import config.Configurable


object SearchResultsConfig extends Configurable {

  override val namespace = "list"
  override val referenceConfigFilename = "list_reference.conf"

  def defaultTagOrder = {
    val tagList = getStringList("default_tag_order")
    if (tagList.isEmpty) {
      List("TAG", "HOSTNAME", "PRIMARY_ROLE", "STATUS", "CREATED_DATE",
          "UPDATED_DATE", "SL_LINK")
    } else {
      tagList
    }
  }

  override def validateConfig() {
    defaultTagOrder
  }

}
