name := "referencebenchmark"

organization := "com.tumblr"

organizationName := "Tumblr"

organizationHomepage := Some(url("http://tumblr.com"))

version := "0.1.0-SNAPSHOT"

resolvers += "Tumblr Internal" at "http://repo.tumblr.net:8081/nexus/content/groups/public/"

testListeners <<= target.map(t => Seq(new eu.henkelmann.sbt.JUnitXmlTestsListener(t.getAbsolutePath)))

libraryDependencies ++= Seq(
	"com.tumblr" %% "specsbench" % "0.1.1-SNAPSHOT"
)

publishTo <<= (version) {version: String =>
  // we want to publish to tumblr/snapshots for snapshot releases, timblr/releases otherwise
  val basePublishUrl = Option(System.getProperty("TUMBLR_PUBLISH_URL")).getOrElse("http://repo.tumblr.net:8081/nexus/content/repositories/")
  val snapshotDeployRepo = "snapshots"
  val releaseDeployRepo = "releases"
  if(version.trim.endsWith("SNAPSHOT"))
    Some("Tumblr Publish Snapshot" at (basePublishUrl + snapshotDeployRepo))
  else
    Some("Tumblr Publish Release" at (basePublishUrl + releaseDeployRepo))
}

credentials += Credentials(Path.userHome / ".ivy2" / ".credentials")