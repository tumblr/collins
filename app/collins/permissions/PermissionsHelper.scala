package collins.permissions

import java.util.{List => JList}
import java.util.{Map => JMap}

import scala.collection.JavaConverters.asScalaBufferConverter
import scala.collection.JavaConverters.mapAsScalaMapConverter
import scala.collection.mutable.HashMap

import com.tumblr.play.interop.PrivilegedHelper

object PermissionsHelper {
  import scala.collection.JavaConverters._
  def fromFile(filename: String): Privileges = {
    val p = PrivilegedHelper.fromFile(filename)
    require(p != null, "Invalid yaml file")
    val users = Users(converted(p.users))
    val perms = Permissions(converted(p.permissions))
    Privileges(users, perms)
  }
  private def converted(map: JMap[String,JList[String]]): Map[String,Set[String]] = {
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
