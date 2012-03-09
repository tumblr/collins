package util
package views

import java.net.URLEncoder

object Pagination {
  val Excludes = Set("page", "size", "sort")

  def paginate(prefix: String, map: Map[String, Seq[String]]): String = {
    mapToQueryString(prefix, map.filter(kv => !Excludes.contains(kv._1)))
  }

  def mapToQueryString(prefix: String, map: Map[String, Seq[String]]): String = {
    val qs = map.map { case(k,v) =>
      v.map{s => "%s=%s".format(k, URLEncoder.encode(s,"UTF-8"))}.mkString("&")
    }.mkString("&")
    prefix match {
      case hasQs if hasQs.contains("?") => hasQs + "&" + qs
      case noQs => noQs + "?" + qs
    }
  }
}
