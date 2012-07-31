package com.tumblr.play

import interop.PrivilegedHelper
import java.util.{List => JList, Map => JMap}
import scala.collection.mutable.HashMap

import play.api.Logger

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

case class Privileges(users: Users, permissions: Permissions) {
  private val logger = Logger(getClass)

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

object PermissionsHelper {
  import scala.collection.JavaConverters._
  def fromFile(filename: String): Privileges = {
    val p = PrivilegedHelper.fromFile(filename)
    require(p != null, "Invalid yaml file")
    val users = Users(converted(p.users))
    val perms = Permissions(converted(p.permissions))
    Privileges(users, perms)
  }
  private
  def converted(map: JMap[String,JList[String]]): Map[String,Set[String]] = {
    map.asScala.map { t =>
      val key = t._1.toLowerCase
      val value = if (t._2 == null) { Set[String]() } else { t._2.asScala.toSet }
      key -> value.map(s => s.toLowerCase)
    }.toMap
  }
  def hashMapWithDefault = new HashMap[String, Set[String]] {
    override def default(key: String) = Set[String]()
  }
}
