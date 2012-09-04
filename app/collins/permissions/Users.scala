package collins.permissions

case class Users(val assets: Map[String, Set[String]]) {
  def this() = this(Map.empty)
  lazy val aliases: Map[String, Set[String]] = invertedMap
  // Turn values into keys, new values are sets of old keys
  // Turns a map of Group -> Users into User -> Groups
  def invertedMap: Map[String, Set[String]] = {
    val map = PermissionsHelper.hashMapWithDefault
    assets.foreach { case (groupName, users) =>
      users.foreach { user =>
        map.update(user, map(user) + groupName)
      }
    }
    map.toMap
  }
}
