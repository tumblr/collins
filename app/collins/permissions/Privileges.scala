package collins.permissions

import play.api.Logger

case class Privileges(users: Users, permissions: Permissions) {
  private val logger = Logger("Privileges")

  def userHasPermission(username: String, subject: String): Boolean = {
    if (!sm(permissions.users, username).contains(subject.toLowerCase)) {
      // username not directly found, let's see if we have an alias
      sm(users.aliases, username) // get aliases for username
        .find(alias => sm(permissions.users, alias).contains(subject.toLowerCase)) // find the first alias that has permissions
        .map(_ => true) // found something, make it true
        .getOrElse(false) // found nothing, make it false
    } else {
      true
    }
  }
  def hasConcern(concern: String): Boolean = {
    val res = permissions.assets.contains(concern.toLowerCase)
    logger.debug("hasConcern(%s) -> %s".format(concern, res.toString))
    res
  }
  def getConcern(concern: String): Set[String] = {
    sm(permissions.assets, concern)
  }
  def groupHasPermission(group: String, subject: String): Boolean = {
    logger.debug("Checking group '%s' in subject '%s'".format(group, subject))
    permissions.groups.get(group.toLowerCase) match {
      case None => false
      case Some(s) => s.isEmpty || s.contains(subject.toLowerCase)
    }
  }

  private def sm(m: Map[String, Set[String]], key: String): Set[String] = {
    m.get(key.toLowerCase) match {
      case None => Set()
      case Some(s) => s
    }
  }
}

object Privileges {
  def empty: Privileges = {
    Privileges(new Users(), new Permissions())
  }
}
