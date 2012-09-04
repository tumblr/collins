package collins.permissions

// Assets is a map of concern -> permissions
case class Permissions(val assets: Map[String, Set[String]]) {
  def this() = this(Map.empty)
  // Map of User -> Permissions
  lazy val users:  Map[String, Set[String]] = converter("u=")
  // Map of Group -> Permission
  lazy val groups: Map[String, Set[String]] = converter("g=")

  private def converter(key: String): Map[String, Set[String]] = {
    val map = PermissionsHelper.hashMapWithDefault
    assets.foreach { case(concern, perms) =>
      perms.filter(_.startsWith(key)).map(_.replace(key, "")).foreach { item =>
        map.update(item, map(item) + concern)
      }
    }
    map.toMap
  }
}
