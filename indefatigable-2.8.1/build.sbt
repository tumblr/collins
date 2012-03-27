name := "indefatigable"

organization := "com.tumblr"

organizationName := "Tumblr"

scalaVersion := "2.8.1"

organizationHomepage := Some(url("http://tumblr.com"))

version := "0.1.0-SNAPSHOT"

resolvers += "Tumblr Internal" at "http://repo.tumblr.net:8081/nexus/content/groups/public/"

resolvers += "Twitter" at "http://maven.twttr.com/"

libraryDependencies ++= Seq(
  "org.scala-tools.testing" % "specs_2.8.1" % "1.6.8",
  "thrift" % "libthrift" % "0.5.0" from "http://maven.twttr.com/thrift/libthrift/0.5.0/libthrift-0.5.0.jar"
)

ivyXML :=
  <dependencies>
    <dependency org="com.tumblr" name="service-util" rev="2.0.1-SNAPSHOT">
      <exclude org="io.netty"/>
    </dependency>
  </dependencies>

parallelExecution := false  

// use `+compile` and `+publish` to compile and publish cross all versions of scala
crossScalaVersions := Seq("2.8.1")

testListeners <<= target.map(t => Seq(new eu.henkelmann.sbt.JUnitXmlTestsListener(t.getAbsolutePath)))

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

// credentials files are of the format:
//
// ```
//   realm=Sonatype Nexus Repository Manager
//   host=repo.tumblr.net
//   user=xxxx
//   password=xxx
// ```
credentials += Credentials(Path.userHome / ".ivy2" / ".credentials")

// PROGUARD PACKAGING SUPPORT
seq(ProguardPlugin.proguardSettings :_*)

proguardOptions ++= Seq(
    keepAllScala ++ """
    -dontobfuscate
    -dontoptimize
    -dontpreverify
    -dontnote
    -dontwarn
    -dontshrink
    -ignorewarnings
    -keepattributes
    -skipnonpubliclibraryclasses
    """
)