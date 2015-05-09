package collins.util

import java.net.URL

object RemoteCollinsHost {
  def apply(url: String) = new RemoteCollinsHost(new URL(url))
}

case class RemoteCollinsHost(url: URL) {
  val credentials = url.getUserInfo().split(":", 2)
  require(credentials.size == 2, "Must have username and password")
  val username = credentials(0)
  val password = credentials(1)
  val path = Option(url.getPath).filter(_.nonEmpty).getOrElse("/").replaceAll("/+$", "")

  def host: String = url.getPort match {
    case none if none < 0 =>
      "%s://%s%s".format(url.getProtocol, url.getHost, path)
    case port =>
      "%s://%s:%d%s".format(url.getProtocol, url.getHost, port, path)
  }

  def hostWithCredentials: String = url.toString.replaceAll("/+$", "")

}
