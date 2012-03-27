import sbt._

class Plugins(info: ProjectInfo) extends PluginDefinition(info) {
  import scala.collection.jcl
  val environment = jcl.Map(System.getenv())

  def proxyRepo = environment.get("TUMBLR_REPO")

  override def repositories = {
    val defaultRepos = List(
      "ibiblio" at "http://mirrors.ibiblio.org/pub/mirrors/maven2/",
      "twitter.com" at "http://maven.twttr.com/",
      "powermock-api" at "http://powermock.googlecode.com/svn/repo/",
      "scala-tools.org" at "http://scala-tools.org/repo-releases/",
      "testing.scala-tools.org" at "http://scala-tools.org/repo-releases/testing/",
      "oauth.net" at "http://oauth.googlecode.com/svn/code/maven",
      "download.java.net" at "http://download.java.net/maven/2/",
      "atlassian" at "https://m2proxy.atlassian.com/repository/public/",
      // for netty:
      "jboss" at "http://repository.jboss.org/nexus/content/groups/public/"
    )
    proxyRepo match {
      case Some(url) =>
        Set("Tumblr Nexus Repo" at url)
      case None =>
        super.repositories ++ Set(defaultRepos: _*)
    }
  }
  override def ivyRepositories = Seq(Resolver.defaultLocal(None)) ++ repositories

  val standardProject = "com.twitter" % "standard-project" % "0.12.12"
  val junitStuff      = "com.tumblr"  % "junit_xml_listener" % "0.4.0-SNAPSHOT"
  val sbtThrift       = "com.twitter" % "sbt-thrift" % "2.1.1"
  val proguard        = "org.scala-tools.sbt" % "sbt-proguard-plugin" % "0.0.5"
}